package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.RenderedDeck;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class SlideGeneratorApp {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path SAMPLE_SLIDE_PATH = Path.of("sample-slide.json");
    private static final Path RENDERED_SLIDE_PATH = Path.of("rendered-slide.json");
    private static final Path SLIDE_HTML_PATH = Path.of("slide.html");
    private static final Path DEFAULT_IMAGE_ASSET_DIR = Path.of("generated-assets/images");
    private static final String USAGE = """
            Usage:
              java -jar target/slide-generator-1.0.0.jar input.json
              java -jar target/slide-generator-1.0.0.jar --html rendered-slide.json
              java -jar target/slide-generator-1.0.0.jar --prompt "create a slide..."
              java -jar target/slide-generator-1.0.0.jar --prompt-to-html "create a slide..."
              java -jar target/slide-generator-1.0.0.jar --edit-to-html "change rectangle in slide 2 to red circle"
            """;

    private SlideGeneratorApp() {
    }

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        Map<String, String> environment = System.getenv();
        return run(
                args,
                new DefaultSlideAstClientFactory(environment),
                new DefaultImageGeneratorClientFactory(environment),
                new DefaultSlideEditClientFactory(environment),
                Path.of("."),
                imageAssetDir(environment),
                imageModel(environment),
                imageQuality(environment)
        );
    }

    static int run(String[] args, SlideAstClientFactory slideAstClientFactory) {
        return run(args, slideAstClientFactory, Path.of("."));
    }

    static int run(String[] args, SlideAstClientFactory slideAstClientFactory, Path outputDir) {
        return run(
                args,
                slideAstClientFactory,
                ImageGeneratorClient::disabled,
                SlideEditClient::disabled,
                outputDir,
                DEFAULT_IMAGE_ASSET_DIR,
                OpenAiImageGeneratorClient.DEFAULT_MODEL,
                OpenAiImageGeneratorClient.DEFAULT_QUALITY
        );
    }

    static int run(
            String[] args,
            SlideAstClientFactory slideAstClientFactory,
            ImageGeneratorClientFactory imageGeneratorClientFactory,
            Path outputDir,
            Path imageAssetDir,
            String imageModel,
            String imageQuality
    ) {
        return run(
                args,
                slideAstClientFactory,
                imageGeneratorClientFactory,
                SlideEditClient::disabled,
                outputDir,
                imageAssetDir,
                imageModel,
                imageQuality
        );
    }

    static int run(
            String[] args,
            SlideAstClientFactory slideAstClientFactory,
            ImageGeneratorClientFactory imageGeneratorClientFactory,
            SlideEditClientFactory slideEditClientFactory,
            Path outputDir,
            Path imageAssetDir,
            String imageModel,
            String imageQuality
    ) {
        if (args.length == 1) {
            return renderLayoutJson(args[0]);
        }
        if (args.length == 2 && "--html".equals(args[0])) {
            return renderHtml(args[1]);
        }
        if (args.length == 2 && "--prompt".equals(args[0])) {
            return generateSampleSlide(args[1], slideAstClientFactory, outputDir);
        }
        if (args.length == 2 && "--prompt-to-html".equals(args[0])) {
            return generateSampleSlideAndHtml(
                    args[1],
                    slideAstClientFactory,
                    imageGeneratorClientFactory,
                    outputDir,
                    imageAssetDir,
                    imageModel,
                    imageQuality
            );
        }
        if (args.length == 2 && "--edit-to-html".equals(args[0])) {
            return editSampleSlideAndHtml(outputDir.resolve(SAMPLE_SLIDE_PATH), args[1], slideEditClientFactory, outputDir);
        }
        if (args.length == 3 && "--edit-to-html".equals(args[0])) {
            return editSampleSlideAndHtml(Path.of(args[1]), args[2], slideEditClientFactory, outputDir);
        }

        System.err.print(USAGE);
        return 2;
    }

    private static int renderLayoutJson(String inputPath) {
        try {
            DeckInput input = MAPPER.readValue(Path.of(inputPath).toFile(), DeckInput.class);
            RenderedDeck renderedDeck = new ComponentDeckRenderer().render(input);
            MAPPER.writeValue(System.out, renderedDeck);
            System.out.println();
            return 0;
        } catch (IOException | SlideLayoutException ex) {
            System.err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static int renderHtml(String inputPath) {
        try {
            RenderedDeck renderedDeck = MAPPER.readValue(Path.of(inputPath).toFile(), RenderedDeck.class);
            System.out.print(new SlideHtmlRenderer().render(renderedDeck));
            return 0;
        } catch (IOException | SlideLayoutException ex) {
            System.err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static int editSampleSlideAndHtml(
            Path deckInputPath,
            String instruction,
            SlideEditClientFactory slideEditClientFactory,
            Path outputDir
    ) {
        try {
            ComponentDeckRenderer deckRenderer = new ComponentDeckRenderer();
            SlideHtmlRenderer htmlRenderer = new SlideHtmlRenderer();
            DeckInput input = MAPPER.readValue(deckInputPath.toFile(), DeckInput.class);
            DeckInput editedDeckInput = new DeckEditService(slideEditClientFactory.create(), deckRenderer).edit(input, instruction);
            RenderedDeck renderedDeck = deckRenderer.render(editedDeckInput);
            String html = htmlRenderer.render(renderedDeck);

            MAPPER.writeValue(deckInputPath.toFile(), editedDeckInput);
            MAPPER.writeValue(outputDir.resolve(RENDERED_SLIDE_PATH).toFile(), renderedDeck);
            Files.writeString(outputDir.resolve(SLIDE_HTML_PATH), html);
            MAPPER.writeValue(System.out, editedDeckInput);
            System.out.println();
            return 0;
        } catch (IOException | InterruptedException | SlideLayoutException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static int generateSampleSlide(String prompt, SlideAstClientFactory slideAstClientFactory, Path outputDir) {
        try {
            PromptToSlideAstService service = promptToSlideAstService(slideAstClientFactory.create());
            DeckInput deckInput = service.generateSampleDeck(prompt, outputDir.resolve(SAMPLE_SLIDE_PATH));
            MAPPER.writeValue(System.out, deckInput);
            System.out.println();
            return 0;
        } catch (IOException | InterruptedException | SlideLayoutException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static int generateSampleSlideAndHtml(String prompt, SlideAstClientFactory slideAstClientFactory, Path outputDir) {
        return generateSampleSlideAndHtml(
                prompt,
                slideAstClientFactory,
                ImageGeneratorClient::disabled,
                outputDir,
                DEFAULT_IMAGE_ASSET_DIR,
                OpenAiImageGeneratorClient.DEFAULT_MODEL,
                OpenAiImageGeneratorClient.DEFAULT_QUALITY
        );
    }

    private static int generateSampleSlideAndHtml(
            String prompt,
            SlideAstClientFactory slideAstClientFactory,
            ImageGeneratorClientFactory imageGeneratorClientFactory,
            Path outputDir,
            Path imageAssetDir,
            String imageModel,
            String imageQuality
    ) {
        try {
            PromptToSlideAstService service = promptToSlideAstService(
                    slideAstClientFactory.create(),
                    imageGeneratorClientFactory.create(),
                    imageAssetDir,
                    imageModel,
                    imageQuality
            );
            DeckInput deckInput = service.generateEndToEnd(
                    prompt,
                    outputDir.resolve(SAMPLE_SLIDE_PATH),
                    outputDir.resolve(RENDERED_SLIDE_PATH),
                    outputDir.resolve(SLIDE_HTML_PATH)
            );
            MAPPER.writeValue(System.out, deckInput);
            System.out.println();
            return 0;
        } catch (IOException | InterruptedException | SlideLayoutException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static PromptToSlideAstService promptToSlideAstService(SlideAstClient slideAstClient) {
        return promptToSlideAstService(
                slideAstClient,
                ImageGeneratorClient.disabled(),
                DEFAULT_IMAGE_ASSET_DIR,
                OpenAiImageGeneratorClient.DEFAULT_MODEL,
                OpenAiImageGeneratorClient.DEFAULT_QUALITY
        );
    }

    private static PromptToSlideAstService promptToSlideAstService(
            SlideAstClient slideAstClient,
            ImageGeneratorClient imageGeneratorClient,
            Path imageAssetDir,
            String imageModel,
            String imageQuality
    ) {
        return new PromptToSlideAstService(
                slideAstClient,
                new ComponentDeckRenderer(),
                new SlideHtmlRenderer(),
                MAPPER,
                imageGeneratorClient,
                imageAssetDir,
                imageModel,
                imageQuality
        );
    }

    @FunctionalInterface
    interface SlideAstClientFactory {
        SlideAstClient create();
    }

    @FunctionalInterface
    interface ImageGeneratorClientFactory {
        ImageGeneratorClient create();
    }

    @FunctionalInterface
    interface SlideEditClientFactory {
        SlideEditClient create();
    }

    private record DefaultSlideAstClientFactory(Map<String, String> environment) implements SlideAstClientFactory {
        @Override
        public SlideAstClient create() {
            return new OpenAiSlideAstClient(
                    environment.get("OPENAI_API_KEY"),
                    environment.get("OPENAI_MODEL"),
                    MAPPER
            );
        }
    }

    private record DefaultImageGeneratorClientFactory(Map<String, String> environment) implements ImageGeneratorClientFactory {
        @Override
        public ImageGeneratorClient create() {
            return new OpenAiImageGeneratorClient(environment.get("OPENAI_API_KEY"), MAPPER);
        }
    }

    private record DefaultSlideEditClientFactory(Map<String, String> environment) implements SlideEditClientFactory {
        @Override
        public SlideEditClient create() {
            return new OpenAiSlideEditClient(
                    environment.get("OPENAI_API_KEY"),
                    environment.get("OPENAI_MODEL"),
                    MAPPER
            );
        }
    }

    private static Path imageAssetDir(Map<String, String> environment) {
        String value = environment.get("OPENAI_IMAGE_ASSET_DIR");
        return value == null || value.isBlank() ? DEFAULT_IMAGE_ASSET_DIR : Path.of(value);
    }

    private static String imageModel(Map<String, String> environment) {
        String value = environment.get("OPENAI_IMAGE_MODEL");
        return value == null || value.isBlank() ? OpenAiImageGeneratorClient.DEFAULT_MODEL : value;
    }

    private static String imageQuality(Map<String, String> environment) {
        String value = environment.get("OPENAI_IMAGE_QUALITY");
        return value == null || value.isBlank() ? OpenAiImageGeneratorClient.DEFAULT_QUALITY : value;
    }
}
