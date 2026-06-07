package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.RenderedDeck;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public final class SlideGeneratorApp {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path SAMPLE_SLIDE_PATH = Path.of("sample-slide.json");
    private static final Path RENDERED_SLIDE_PATH = Path.of("rendered-slide.json");
    private static final Path SLIDE_HTML_PATH = Path.of("slide.html");
    private static final String USAGE = """
            Usage:
              java -jar target/slide-generator-1.0.0.jar input.json
              java -jar target/slide-generator-1.0.0.jar --html rendered-slide.json
              java -jar target/slide-generator-1.0.0.jar --prompt "create a slide..."
              java -jar target/slide-generator-1.0.0.jar --prompt-to-html "create a slide..."
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
        return run(args, new DefaultSlideAstClientFactory(System.getenv()), Path.of("."));
    }

    static int run(String[] args, SlideAstClientFactory slideAstClientFactory) {
        return run(args, slideAstClientFactory, Path.of("."));
    }

    static int run(String[] args, SlideAstClientFactory slideAstClientFactory, Path outputDir) {
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
            return generateSampleSlideAndHtml(args[1], slideAstClientFactory, outputDir);
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
        try {
            PromptToSlideAstService service = promptToSlideAstService(slideAstClientFactory.create());
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
        return new PromptToSlideAstService(
                slideAstClient,
                new ComponentDeckRenderer(),
                new SlideHtmlRenderer(),
                MAPPER
        );
    }

    @FunctionalInterface
    interface SlideAstClientFactory {
        SlideAstClient create();
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
}
