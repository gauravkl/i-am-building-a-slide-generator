package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.RenderedDeck;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptToSlideAstService {
    private static final int MAX_GENERATION_ATTEMPTS = 2;
    private static final Path DEFAULT_IMAGE_ASSET_DIR = Path.of("generated-assets/images");
    private static final Pattern DIGIT_MATRIX_DIMENSIONS = Pattern.compile("(\\d+)\\s*(?:x|×|by)\\s*(\\d+)");
    private static final Pattern WORD_MATRIX_DIMENSIONS = Pattern.compile(
            "(one|two|three|four|five|six|seven|eight|nine|ten)\\s+by\\s+(one|two|three|four|five|six|seven|eight|nine|ten)"
    );

    private final SlideAstClient slideAstClient;
    private final ComponentDeckRenderer deckRenderer;
    private final SlideHtmlRenderer htmlRenderer;
    private final ObjectMapper objectMapper;
    private final ImageGeneratorClient imageGeneratorClient;
    private final Path imageAssetDir;
    private final String imageModel;
    private final String imageQuality;

    public PromptToSlideAstService(
            SlideAstClient slideAstClient,
            ComponentDeckRenderer deckRenderer,
            SlideHtmlRenderer htmlRenderer,
            ObjectMapper objectMapper
    ) {
        this(
                slideAstClient,
                deckRenderer,
                htmlRenderer,
                objectMapper,
                ImageGeneratorClient.disabled(),
                DEFAULT_IMAGE_ASSET_DIR,
                OpenAiImageGeneratorClient.DEFAULT_MODEL,
                OpenAiImageGeneratorClient.DEFAULT_QUALITY
        );
    }

    public PromptToSlideAstService(
            SlideAstClient slideAstClient,
            ComponentDeckRenderer deckRenderer,
            SlideHtmlRenderer htmlRenderer,
            ObjectMapper objectMapper,
            ImageGeneratorClient imageGeneratorClient,
            Path imageAssetDir,
            String imageModel,
            String imageQuality
    ) {
        this.slideAstClient = slideAstClient;
        this.deckRenderer = deckRenderer;
        this.htmlRenderer = htmlRenderer;
        this.objectMapper = objectMapper;
        this.imageGeneratorClient = imageGeneratorClient == null ? ImageGeneratorClient.disabled() : imageGeneratorClient;
        this.imageAssetDir = imageAssetDir == null ? DEFAULT_IMAGE_ASSET_DIR : imageAssetDir;
        this.imageModel = imageModel == null || imageModel.isBlank() ? OpenAiImageGeneratorClient.DEFAULT_MODEL : imageModel;
        this.imageQuality = normalizeImageQuality(imageQuality);
    }

    public DeckInput generateSampleDeck(String prompt, Path sampleSlidePath) throws IOException, InterruptedException {
        DeckInput deckInput = generateValidatedDeck(prompt);
        objectMapper.writeValue(sampleSlidePath.toFile(), deckInput);
        return deckInput;
    }

    public DeckInput generateEndToEnd(
            String prompt,
            Path sampleSlidePath,
            Path renderedSlidePath,
            Path htmlPath
    ) throws IOException, InterruptedException {
        DeckInput deckInput = resolveGeneratedImages(generateValidatedDeck(prompt), htmlPath);
        RenderedDeck renderedDeck = deckRenderer.render(deckInput);
        String html = htmlRenderer.render(renderedDeck);

        objectMapper.writeValue(sampleSlidePath.toFile(), deckInput);
        objectMapper.writeValue(renderedSlidePath.toFile(), renderedDeck);
        Files.writeString(htmlPath, html);
        return deckInput;
    }

    private DeckInput resolveGeneratedImages(DeckInput deckInput, Path htmlPath) throws IOException, InterruptedException {
        if (deckInput == null || deckInput.slides() == null || !containsUnresolvedImage(deckInput)) {
            return deckInput;
        }

        Path htmlDir = htmlDirectory(htmlPath);
        Path resolvedAssetDir = resolvedImageAssetDir(htmlDir);
        Files.createDirectories(resolvedAssetDir);

        List<SlideSpec> resolvedSlides = new ArrayList<>();
        for (SlideSpec slide : deckInput.slides()) {
            if (slide == null || slide.components() == null) {
                resolvedSlides.add(slide);
                continue;
            }

            List<SlideComponent> resolvedComponents = new ArrayList<>();
            for (SlideComponent component : slide.components()) {
                resolvedComponents.add(resolveGeneratedImage(slide, component, htmlDir, resolvedAssetDir));
            }
            resolvedSlides.add(new SlideSpec(slide.id(), slide.type(), List.copyOf(resolvedComponents)));
        }

        return new DeckInput(deckInput.type(), deckInput.size(), List.copyOf(resolvedSlides));
    }

    private SlideComponent resolveGeneratedImage(
            SlideSpec slide,
            SlideComponent component,
            Path htmlDir,
            Path resolvedAssetDir
    ) throws IOException, InterruptedException {
        if (component == null || !"image".equals(component.type()) || (component.src() != null && !component.src().isBlank())) {
            return component;
        }

        String prompt = requireNonBlank(component.imagePrompt(), "Image component requires imagePrompt before asset generation: " + component.id());
        String alt = requireNonBlank(component.alt(), "Image component requires alt text: " + component.id());
        String size = imageSize(component.bounds());
        ImageGenerationOptions options = new ImageGenerationOptions(imageModel, imageQuality, size);
        String hash = imageHash(prompt, options);
        Path assetPath = resolvedAssetDir.resolve(assetFileName(slide.id(), component.id(), hash));
        if (!Files.exists(assetPath)) {
            Files.write(assetPath, imageGeneratorClient.generateImage(prompt, options));
        }

        return new SlideComponent(
                component.id(),
                component.type(),
                component.text(),
                component.items(),
                component.headers(),
                component.rows(),
                component.imagePrompt(),
                htmlSrc(htmlDir, assetPath),
                alt,
                component.bounds(),
                component.style()
        );
    }

    private static boolean containsUnresolvedImage(DeckInput deckInput) {
        return deckInput.slides().stream()
                .filter(slide -> slide != null && slide.components() != null)
                .flatMap(slide -> slide.components().stream())
                .anyMatch(component -> component != null
                        && "image".equals(component.type())
                        && (component.src() == null || component.src().isBlank()));
    }

    private DeckInput generateValidatedDeck(String prompt) throws IOException, InterruptedException {
        SlideLayoutException lastIntentError = null;
        DeckInput lastDeckInput = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            DeckInput deckInput = repairTableColumnMismatches(slideAstClient.generateDeck(attempt == 0 ? prompt : correctionPrompt(prompt, lastIntentError)));
            lastDeckInput = deckInput;
            deckRenderer.validateForAssetGeneration(deckInput);
            try {
                validateRequestedComponentTypes(prompt, deckInput);
                validateRequestedMatrixDimensions(prompt, deckInput);
                return deckInput;
            } catch (SlideLayoutException ex) {
                lastIntentError = ex;
            }
        }

        if (lastDeckInput != null) {
            DeckInput repairedDeckInput = repairRequestedMatrixDimensions(prompt, repairTableColumnMismatches(lastDeckInput));
            if (repairedDeckInput != lastDeckInput) {
                deckRenderer.validateForAssetGeneration(repairedDeckInput);
                validateRequestedComponentTypes(prompt, repairedDeckInput);
                validateRequestedMatrixDimensions(prompt, repairedDeckInput);
                return repairedDeckInput;
            }
        }

        throw lastIntentError == null
                ? new SlideLayoutException("Generated AST failed prompt component validation.")
                : lastIntentError;
    }

    private static String correctionPrompt(String prompt, SlideLayoutException intentError) {
        return prompt + """

                Correction from the AST validator:
                """
                + intentError.getMessage()
                + """

                Use the actual requested core component type instead of drawing it manually with other pieces.
                Examples:
                - A requested matrix must be one component with "type": "matrix" and a "rows" array.
                - If the prompt requested a 2x2 matrix, rows must contain exactly 2 arrays and each row must contain exactly 2 cells.
                - For a 2x2 matrix, the shape must look like: "rows": [["complete idea A", "complete idea B"], ["complete idea C", "complete idea D"]].
                - Do not return 4 rows for a 2x2 matrix.
                - Do not split one complete idea across extra matrix cells to create more rows or columns.
                - A requested table must be one component with "type": "table", "headers", and "rows".
                - A table must have the same number of headers as cells in every row.
                - If a table has 3 headers, every row must have exactly 3 cells.
                - Requested bullets must be one component with "type": "bullets" and "items".
                - Requested arrow, circle, and rectangle shapes must use "arrow", "circle", and "rect".
                - A requested image must be one component with "type": "image", "imagePrompt", and "alt"; Java will fill "src".
                - A requested chart or graph must be one component with "type": "chart", "chartType": "bar", "labels", and "values".
                - A chart must have the same number of labels and values, and every value must be a non-negative number.
                Return only valid DeckInput JSON.
                """;
    }

    private static void validateRequestedComponentTypes(String prompt, DeckInput deckInput) {
        for (String requestedType : requestedComponentTypes(prompt)) {
            if (!containsComponentType(deckInput, requestedType)) {
                throw new SlideLayoutException("Prompt requested a " + requestedType
                        + " component, but generated AST did not include any component with type '" + requestedType + "'.");
            }
        }
    }

    private static void validateRequestedMatrixDimensions(String prompt, DeckInput deckInput) {
        MatrixDimensions requested = requestedMatrixDimensions(prompt);
        if (requested == null) {
            return;
        }

        for (SlideComponent matrix : matrixComponents(deckInput)) {
            MatrixDimensions actual = actualMatrixDimensions(matrix);
            if (!requested.equals(actual)) {
                throw new SlideLayoutException("Prompt requested a "
                        + requested.rows()
                        + "x"
                        + requested.columns()
                        + " matrix, but matrix component '"
                        + matrix.id()
                        + "' is "
                        + actual.rows()
                        + "x"
                        + actual.columns()
                        + ".");
            }
        }
    }

    private static DeckInput repairTableColumnMismatches(DeckInput deckInput) {
        if (deckInput == null || deckInput.slides() == null) {
            return deckInput;
        }

        boolean changed = false;
        List<SlideSpec> repairedSlides = new ArrayList<>();
        for (SlideSpec slide : deckInput.slides()) {
            if (slide == null || slide.components() == null) {
                repairedSlides.add(slide);
                continue;
            }

            List<SlideComponent> repairedComponents = new ArrayList<>();
            for (SlideComponent component : slide.components()) {
                if (component != null && "table".equals(component.type()) && tableNeedsColumnRepair(component)) {
                    repairedComponents.add(repairTableComponent(component));
                    changed = true;
                } else {
                    repairedComponents.add(component);
                }
            }
            repairedSlides.add(new SlideSpec(slide.id(), slide.type(), List.copyOf(repairedComponents)));
        }

        return changed
                ? new DeckInput(deckInput.type(), deckInput.size(), List.copyOf(repairedSlides))
                : deckInput;
    }

    private static boolean tableNeedsColumnRepair(SlideComponent table) {
        if (table.headers() == null || table.headers().isEmpty() || table.rows() == null || table.rows().isEmpty()) {
            return false;
        }
        int expectedColumns = table.headers().size();
        for (List<String> row : table.rows()) {
            if (row == null || row.size() != expectedColumns) {
                return true;
            }
        }
        return false;
    }

    private static SlideComponent repairTableComponent(SlideComponent table) {
        int targetColumns = targetTableColumnCount(table);
        List<String> repairedHeaders = repairHeaders(table.headers(), targetColumns);
        List<List<String>> repairedRows = new ArrayList<>();
        for (List<String> row : table.rows()) {
            repairedRows.add(repairRow(row, targetColumns));
        }

        return new SlideComponent(
                table.id(),
                table.type(),
                table.text(),
                table.items(),
                List.copyOf(repairedHeaders),
                List.copyOf(repairedRows),
                table.bounds(),
                table.style()
        );
    }

    private static int targetTableColumnCount(SlideComponent table) {
        int firstRowColumnCount = 0;
        boolean rowsAreConsistent = true;
        if (table.rows() != null) {
            for (List<String> row : table.rows()) {
                int rowSize = row == null ? 0 : row.size();
                if (firstRowColumnCount == 0) {
                    firstRowColumnCount = rowSize;
                } else if (rowSize != firstRowColumnCount) {
                    rowsAreConsistent = false;
                }
            }
        }

        if (rowsAreConsistent && firstRowColumnCount > 0) {
            return firstRowColumnCount;
        }
        if (table.headers() != null && !table.headers().isEmpty()) {
            return table.headers().size();
        }
        return Math.max(1, firstRowColumnCount);
    }

    private static List<String> repairHeaders(List<String> headers, int targetColumns) {
        List<String> repairedHeaders = new ArrayList<>();
        List<String> safeHeaders = headers == null ? List.of() : headers;
        for (int columnIndex = 0; columnIndex < targetColumns; columnIndex++) {
            if (columnIndex < safeHeaders.size() && safeHeaders.get(columnIndex) != null && !safeHeaders.get(columnIndex).isBlank()) {
                repairedHeaders.add(safeHeaders.get(columnIndex));
            } else {
                repairedHeaders.add("Column " + (columnIndex + 1));
            }
        }
        return repairedHeaders;
    }

    private static List<String> repairRow(List<String> row, int targetColumns) {
        List<String> safeRow = row == null ? List.of() : row;
        if (safeRow.size() == targetColumns) {
            return List.copyOf(safeRow);
        }
        if (safeRow.size() < targetColumns) {
            List<String> paddedRow = new ArrayList<>(safeRow);
            while (paddedRow.size() < targetColumns) {
                paddedRow.add("");
            }
            return List.copyOf(paddedRow);
        }

        List<String> repairedRow = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < targetColumns; columnIndex++) {
            int start = Math.floorDiv(columnIndex * safeRow.size(), targetColumns);
            int end = Math.floorDiv((columnIndex + 1) * safeRow.size(), targetColumns);
            if (end <= start) {
                end = Math.min(start + 1, safeRow.size());
            }
            repairedRow.add(joinCellChunk(safeRow.subList(start, end)));
        }
        return List.copyOf(repairedRow);
    }

    private static DeckInput repairRequestedMatrixDimensions(String prompt, DeckInput deckInput) {
        MatrixDimensions requested = requestedMatrixDimensions(prompt);
        if (requested == null || deckInput == null || deckInput.slides() == null) {
            return deckInput;
        }

        boolean changed = false;
        List<SlideSpec> repairedSlides = new ArrayList<>();
        for (SlideSpec slide : deckInput.slides()) {
            if (slide == null || slide.components() == null) {
                repairedSlides.add(slide);
                continue;
            }

            List<SlideComponent> repairedComponents = new ArrayList<>();
            for (SlideComponent component : slide.components()) {
                if (component != null && "matrix".equals(component.type()) && !requested.equals(actualMatrixDimensions(component))) {
                    repairedComponents.add(repairMatrixComponent(component, requested));
                    changed = true;
                } else {
                    repairedComponents.add(component);
                }
            }
            repairedSlides.add(new SlideSpec(slide.id(), slide.type(), List.copyOf(repairedComponents)));
        }

        return changed
                ? new DeckInput(deckInput.type(), deckInput.size(), List.copyOf(repairedSlides))
                : deckInput;
    }

    private static SlideComponent repairMatrixComponent(SlideComponent matrix, MatrixDimensions requested) {
        List<String> sourceCells = matrixSourceCells(matrix);
        int targetCellCount = requested.rows() * requested.columns();
        List<String> targetCells = new ArrayList<>();

        if (sourceCells.size() == targetCellCount) {
            targetCells.addAll(sourceCells);
        } else {
            for (int cellIndex = 0; cellIndex < targetCellCount; cellIndex++) {
                int start = Math.floorDiv(cellIndex * sourceCells.size(), targetCellCount);
                int end = Math.floorDiv((cellIndex + 1) * sourceCells.size(), targetCellCount);
                if (end <= start) {
                    end = Math.min(start + 1, sourceCells.size());
                }
                targetCells.add(joinCellChunk(sourceCells.subList(start, end)));
            }
        }

        while (targetCells.size() < targetCellCount) {
            targetCells.add("");
        }

        List<List<String>> repairedRows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < requested.rows(); rowIndex++) {
            List<String> row = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < requested.columns(); columnIndex++) {
                row.add(targetCells.get(rowIndex * requested.columns() + columnIndex));
            }
            repairedRows.add(List.copyOf(row));
        }

        return new SlideComponent(
                matrix.id(),
                matrix.type(),
                matrix.text(),
                matrix.items(),
                matrix.headers(),
                List.copyOf(repairedRows),
                matrix.bounds(),
                matrix.style()
        );
    }

    private static List<String> matrixSourceCells(SlideComponent matrix) {
        List<String> cells = new ArrayList<>();
        if (matrix.rows() == null) {
            return cells;
        }

        MatrixDimensions actual = actualMatrixDimensions(matrix);
        int targetCellCountCandidate = matrix.rows().size();
        if (actual.rows() == targetCellCountCandidate && actual.columns() > 1) {
            for (List<String> row : matrix.rows()) {
                cells.add(joinCellChunk(row));
            }
            return cells;
        }

        for (List<String> row : matrix.rows()) {
            if (row != null) {
                cells.addAll(row);
            }
        }
        return cells;
    }

    private static String joinCellChunk(List<String> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        List<String> values = chunk.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.get(0);
        }
        return values.get(0) + ": " + String.join(" ", values.subList(1, values.size()));
    }

    private static Set<String> requestedComponentTypes(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        Set<String> requestedTypes = new LinkedHashSet<>();
        if (normalized.contains("bullet")) {
            requestedTypes.add("bullets");
        }
        if (normalized.contains("rectangle") || normalized.contains(" rect ")) {
            requestedTypes.add("rect");
        }
        if (normalized.contains("circle")) {
            requestedTypes.add("circle");
        }
        if (normalized.contains("arrow")) {
            requestedTypes.add("arrow");
        }
        if (normalized.contains("matrix")) {
            requestedTypes.add("matrix");
        }
        if (normalized.contains("table")) {
            requestedTypes.add("table");
        }
        if (normalized.contains("image")
                || normalized.contains("picture")
                || normalized.contains("photo")
                || normalized.contains("illustration")) {
            requestedTypes.add("image");
        }
        if (normalized.contains("chart")
                || normalized.contains("graph")
                || normalized.contains("metric comparison")
                || normalized.contains("values visualization")
                || normalized.contains("bar chart")
                || normalized.contains("trend")) {
            requestedTypes.add("chart");
        }
        return requestedTypes;
    }

    private static Path htmlDirectory(Path htmlPath) {
        Path absoluteHtmlPath = htmlPath == null
                ? Path.of("slide.html").toAbsolutePath().normalize()
                : htmlPath.toAbsolutePath().normalize();
        Path parent = absoluteHtmlPath.getParent();
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }

    private Path resolvedImageAssetDir(Path htmlDir) {
        return imageAssetDir.isAbsolute()
                ? imageAssetDir.normalize()
                : htmlDir.resolve(imageAssetDir).normalize();
    }

    private static String htmlSrc(Path htmlDir, Path assetPath) {
        Path absoluteAssetPath = assetPath.toAbsolutePath().normalize();
        try {
            return htmlDir.relativize(absoluteAssetPath).toString().replace('\\', '/');
        } catch (IllegalArgumentException ex) {
            return absoluteAssetPath.toString().replace('\\', '/');
        }
    }

    private static String imageSize(com.example.slidegen.model.Bounds bounds) {
        if (bounds != null && bounds.w() > bounds.h() * 1.2) {
            return "1536x1024";
        }
        if (bounds != null && bounds.h() > bounds.w() * 1.2) {
            return "1024x1536";
        }
        return "1024x1024";
    }

    private static String assetFileName(String slideId, String componentId, String hash) {
        return safeFilePart(slideId) + "-" + safeFilePart(componentId) + "-" + hash + ".png";
    }

    private static String safeFilePart(String value) {
        String safe = (value == null ? "" : value).replaceAll("[^A-Za-z0-9._-]+", "-");
        safe = safe.replaceAll("^-+", "").replaceAll("-+$", "");
        return safe.isBlank() ? "image" : safe;
    }

    private static String imageHash(String prompt, ImageGenerationOptions options) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = prompt
                    + "\nmodel=" + options.model()
                    + "\nquality=" + options.quality()
                    + "\nsize=" + options.size();
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 6; index++) {
                builder.append(String.format("%02x", hash[index] & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static String normalizeImageQuality(String imageQuality) {
        if (imageQuality == null || imageQuality.isBlank()) {
            return OpenAiImageGeneratorClient.DEFAULT_QUALITY;
        }
        String normalized = imageQuality.toLowerCase(Locale.ROOT);
        if (Set.of("low", "medium", "high").contains(normalized)) {
            return normalized;
        }
        throw new SlideLayoutException("OPENAI_IMAGE_QUALITY must be low, medium, or high.");
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SlideLayoutException(message);
        }
        return value;
    }

    private static boolean containsComponentType(DeckInput deckInput, String requestedType) {
        if (deckInput == null || deckInput.slides() == null) {
            return false;
        }
        return deckInput.slides().stream()
                .filter(slide -> slide != null && slide.components() != null)
                .flatMap(slide -> slide.components().stream())
                .anyMatch(component -> component != null && requestedType.equals(component.type()));
    }

    private static MatrixDimensions requestedMatrixDimensions(String prompt) {
        String normalized = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        if (!normalized.contains("matrix")) {
            return null;
        }

        Matcher digitMatcher = DIGIT_MATRIX_DIMENSIONS.matcher(normalized);
        while (digitMatcher.find()) {
            int rows = Integer.parseInt(digitMatcher.group(1));
            int columns = Integer.parseInt(digitMatcher.group(2));
            if (rows > 0 && columns > 0 && !(rows == 1280 && columns == 720)) {
                return new MatrixDimensions(rows, columns);
            }
        }

        Matcher wordMatcher = WORD_MATRIX_DIMENSIONS.matcher(normalized);
        if (wordMatcher.find()) {
            return new MatrixDimensions(wordNumber(wordMatcher.group(1)), wordNumber(wordMatcher.group(2)));
        }

        return null;
    }

    private static int wordNumber(String value) {
        return switch (value) {
            case "one" -> 1;
            case "two" -> 2;
            case "three" -> 3;
            case "four" -> 4;
            case "five" -> 5;
            case "six" -> 6;
            case "seven" -> 7;
            case "eight" -> 8;
            case "nine" -> 9;
            case "ten" -> 10;
            default -> throw new SlideLayoutException("Unsupported matrix dimension word: " + value);
        };
    }

    private static List<SlideComponent> matrixComponents(DeckInput deckInput) {
        List<SlideComponent> matrices = new ArrayList<>();
        if (deckInput == null || deckInput.slides() == null) {
            return matrices;
        }
        deckInput.slides().stream()
                .filter(slide -> slide != null && slide.components() != null)
                .flatMap(slide -> slide.components().stream())
                .filter(component -> component != null && "matrix".equals(component.type()))
                .forEach(matrices::add);
        return matrices;
    }

    private static MatrixDimensions actualMatrixDimensions(SlideComponent matrix) {
        if (matrix.rows() == null || matrix.rows().isEmpty()) {
            return new MatrixDimensions(0, 0);
        }
        int columns = matrix.rows().get(0) == null ? 0 : matrix.rows().get(0).size();
        for (List<String> row : matrix.rows()) {
            if (row == null || row.size() != columns) {
                return new MatrixDimensions(matrix.rows().size(), -1);
            }
        }
        return new MatrixDimensions(matrix.rows().size(), columns);
    }

    private record MatrixDimensions(int rows, int columns) {
    }
}
