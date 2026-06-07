package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.RenderedDeck;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSpec;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptToSlideAstService {
    private static final int MAX_GENERATION_ATTEMPTS = 2;
    private static final Pattern DIGIT_MATRIX_DIMENSIONS = Pattern.compile("(\\d+)\\s*(?:x|×|by)\\s*(\\d+)");
    private static final Pattern WORD_MATRIX_DIMENSIONS = Pattern.compile(
            "(one|two|three|four|five|six|seven|eight|nine|ten)\\s+by\\s+(one|two|three|four|five|six|seven|eight|nine|ten)"
    );

    private final SlideAstClient slideAstClient;
    private final ComponentDeckRenderer deckRenderer;
    private final SlideHtmlRenderer htmlRenderer;
    private final ObjectMapper objectMapper;

    public PromptToSlideAstService(
            SlideAstClient slideAstClient,
            ComponentDeckRenderer deckRenderer,
            SlideHtmlRenderer htmlRenderer,
            ObjectMapper objectMapper
    ) {
        this.slideAstClient = slideAstClient;
        this.deckRenderer = deckRenderer;
        this.htmlRenderer = htmlRenderer;
        this.objectMapper = objectMapper;
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
        DeckInput deckInput = generateValidatedDeck(prompt);
        RenderedDeck renderedDeck = deckRenderer.render(deckInput);
        String html = htmlRenderer.render(renderedDeck);

        objectMapper.writeValue(sampleSlidePath.toFile(), deckInput);
        objectMapper.writeValue(renderedSlidePath.toFile(), renderedDeck);
        Files.writeString(htmlPath, html);
        return deckInput;
    }

    private DeckInput generateValidatedDeck(String prompt) throws IOException, InterruptedException {
        SlideLayoutException lastIntentError = null;
        DeckInput lastDeckInput = null;
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            DeckInput deckInput = repairTableColumnMismatches(slideAstClient.generateDeck(attempt == 0 ? prompt : correctionPrompt(prompt, lastIntentError)));
            lastDeckInput = deckInput;
            deckRenderer.render(deckInput);
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
                deckRenderer.render(repairedDeckInput);
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

                Use the actual requested Core 7 component type instead of drawing it manually with rect/text pieces.
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
        return requestedTypes;
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
