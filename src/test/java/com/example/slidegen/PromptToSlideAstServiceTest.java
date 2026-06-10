package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PromptToSlideAstServiceTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    @Test
    void generatesValidatedAstAndWritesSampleSlide() throws Exception {
        DeckInput expected = sampleDeck();
        PromptToSlideAstService service = service(prompt -> expected);
        Path samplePath = tempDir.resolve("sample-slide.json");

        DeckInput generated = service.generateSampleDeck("make a deck", samplePath);

        assertEquals(expected, generated);
        DeckInput written = MAPPER.readValue(samplePath.toFile(), DeckInput.class);
        assertEquals(expected, written);
    }

    @Test
    void invalidAstDoesNotOverwriteExistingSampleSlide() throws Exception {
        DeckInput invalid = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(new SlideComponent("bad", "unknown", null, null, null, null, new Bounds(80, 80, 100, 100), null))
                ))
        );
        PromptToSlideAstService service = service(prompt -> invalid);
        Path samplePath = tempDir.resolve("sample-slide.json");
        Files.writeString(samplePath, "old content");

        assertThrows(SlideLayoutException.class, () -> service.generateSampleDeck("make a deck", samplePath));
        assertEquals("old content", Files.readString(samplePath));
    }

    @Test
    void endToEndWritesAstRenderedDeckAndHtml() throws Exception {
        DeckInput expected = sampleDeck();
        PromptToSlideAstService service = service(prompt -> expected);
        Path samplePath = tempDir.resolve("sample-slide.json");
        Path renderedPath = tempDir.resolve("rendered-slide.json");
        Path htmlPath = tempDir.resolve("slide.html");

        DeckInput generated = service.generateEndToEnd("make a deck", samplePath, renderedPath, htmlPath);

        assertEquals(expected, generated);
        assertTrue(Files.readString(samplePath).contains("\"type\" : \"Deck\""));
        assertTrue(Files.readString(renderedPath).contains("\"type\" : \"RenderedDeck\""));
        assertTrue(Files.readString(htmlPath).contains("<main class=\"deck\""));
    }

    @Test
    void endToEndGeneratesImageAssetsAndWritesReferences() throws Exception {
        RecordingImageGeneratorClient imageClient = new RecordingImageGeneratorClient(new byte[]{1, 2, 3, 4});
        PromptToSlideAstService service = service(prompt -> imageDeck(), imageClient);
        Path samplePath = tempDir.resolve("sample-slide.json");
        Path renderedPath = tempDir.resolve("rendered-slide.json");
        Path htmlPath = tempDir.resolve("slide.html");

        DeckInput generated = service.generateEndToEnd(
                "make a slide with a generated image of solar panels",
                samplePath,
                renderedPath,
                htmlPath
        );

        SlideComponent image = generated.slides().get(0).components().get(0);
        assertTrue(image.src().startsWith("generated-assets/images/slide_1-hero_image-"));
        assertTrue(image.src().endsWith(".png"));
        assertTrue(Files.exists(tempDir.resolve(image.src())));
        assertEquals(1, imageClient.calls().size());
        assertEquals("A cinematic illustration of futuristic solar panels", imageClient.calls().get(0).prompt());
        assertEquals("gpt-image-2", imageClient.calls().get(0).options().model());
        assertEquals("medium", imageClient.calls().get(0).options().quality());
        assertEquals("1536x1024", imageClient.calls().get(0).options().size());
        assertTrue(Files.readString(samplePath).contains("\"src\" : \"" + image.src() + "\""));
        assertTrue(Files.readString(renderedPath).contains("\"src\" : \"" + image.src() + "\""));
        assertTrue(Files.readString(htmlPath).contains("<img src=\"" + image.src() + "\" alt=\"Solar panels\">"));

        service.generateEndToEnd(
                "make a slide with a generated image of solar panels",
                tempDir.resolve("sample-slide-2.json"),
                tempDir.resolve("rendered-slide-2.json"),
                tempDir.resolve("slide-2.html")
        );

        assertEquals(1, imageClient.calls().size());
    }

    @Test
    void retriesWhenPromptRequestedMatrixButAstDrawsItWithRectsAndText() throws Exception {
        RecordingSlideAstClient client = new RecordingSlideAstClient(manualMatrixDeck(), matrixDeck());
        PromptToSlideAstService service = service(client);
        Path samplePath = tempDir.resolve("sample-slide.json");

        DeckInput generated = service.generateSampleDeck("make a 2x2 matrix about slide iteration", samplePath);

        assertEquals("matrix", generated.slides().get(0).components().get(1).type());
        assertEquals(2, client.prompts().size());
        assertTrue(client.prompts().get(1).contains("Correction from the AST validator"));
        assertTrue(Files.readString(samplePath).contains("\"type\" : \"matrix\""));
    }

    @Test
    void retriesWhenPromptRequestedChartButAstDrawsItWithRectsAndText() throws Exception {
        RecordingSlideAstClient client = new RecordingSlideAstClient(manualChartDeck(), chartDeck());
        PromptToSlideAstService service = service(client);
        Path samplePath = tempDir.resolve("sample-slide.json");

        DeckInput generated = service.generateSampleDeck("make a bar chart comparing adoption rates", samplePath);

        SlideComponent chart = generated.slides().get(0).components().get(1);
        assertEquals("chart", chart.type());
        assertEquals("bar", chart.chartType());
        assertEquals(List.of("Team A", "Team B", "Team C"), chart.labels());
        assertEquals(List.of(30.0, 45.0, 60.0), chart.values());
        assertEquals(2, client.prompts().size());
        assertTrue(client.prompts().get(1).contains("Correction from the AST validator"));
        assertTrue(client.prompts().get(1).contains("type\": \"chart\""));
        assertTrue(Files.readString(samplePath).contains("\"type\" : \"chart\""));
    }

    @Test
    void retriesWhenPromptRequestedTwoByTwoMatrixButAstUsesWrongDimensions() throws Exception {
        RecordingSlideAstClient client = new RecordingSlideAstClient(wrongDimensionMatrixDeck(), matrixDeck());
        PromptToSlideAstService service = service(client);
        Path samplePath = tempDir.resolve("sample-slide.json");

        DeckInput generated = service.generateSampleDeck("make a 2x2 matrix about slide iteration", samplePath);

        List<List<String>> rows = generated.slides().get(0).components().get(1).rows();
        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).size());
        assertEquals(2, client.prompts().size());
        assertTrue(client.prompts().get(1).contains("Prompt requested a 2x2 matrix"));
    }

    @Test
    void repairsMatrixDimensionsWhenRetryStillUsesWrongDimensions() throws Exception {
        RecordingSlideAstClient client = new RecordingSlideAstClient(wrongDimensionMatrixDeck(), wrongDimensionMatrixDeck());
        PromptToSlideAstService service = service(client);
        Path samplePath = tempDir.resolve("sample-slide.json");

        DeckInput generated = service.generateSampleDeck("make a 2x2 matrix about slide iteration", samplePath);

        List<List<String>> rows = generated.slides().get(0).components().get(1).rows();
        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).size());
        assertEquals("User wants: Style intent preserved", rows.get(0).get(0));
        assertEquals("AI outputs: Plausible layout altered hierarchy", rows.get(0).get(1));
        assertEquals(2, client.prompts().size());
        assertTrue(Files.readString(samplePath).contains("\"rows\" : [ [ \"User wants: Style intent preserved\", \"AI outputs: Plausible layout altered hierarchy\" ]"));
    }

    @Test
    void repairsTableWhenHeaderCountDoesNotMatchRowColumnCount() throws Exception {
        PromptToSlideAstService service = service(prompt -> wrongHeaderCountTableDeck());
        Path samplePath = tempDir.resolve("sample-slide.json");

        DeckInput generated = service.generateSampleDeck("make a table comparing iteration modes", samplePath);

        SlideComponent table = generated.slides().get(0).components().get(1);
        assertEquals(List.of("Mode", "Behavior", "Column 3"), table.headers());
        assertEquals(3, table.rows().get(0).size());
        assertTrue(Files.readString(samplePath).contains("\"headers\" : [ \"Mode\", \"Behavior\", \"Column 3\" ]"));
    }

    private static PromptToSlideAstService service(SlideAstClient slideAstClient) {
        return new PromptToSlideAstService(
                slideAstClient,
                new ComponentDeckRenderer(),
                new SlideHtmlRenderer(),
                MAPPER
        );
    }

    private PromptToSlideAstService service(SlideAstClient slideAstClient, ImageGeneratorClient imageGeneratorClient) {
        return new PromptToSlideAstService(
                slideAstClient,
                new ComponentDeckRenderer(),
                new SlideHtmlRenderer(),
                MAPPER,
                imageGeneratorClient,
                tempDir.resolve("generated-assets/images"),
                "gpt-image-2",
                "medium"
        );
    }

    private static DeckInput sampleDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(
                        new SlideSpec(
                                "slide_1",
                                "Slide",
                                List.of(
                                        new SlideComponent(
                                                "title",
                                                "text",
                                                "Why AI slide iteration breaks",
                                                null,
                                                null,
                                                null,
                                                new Bounds(80, 48, 720, 80),
                                                new Style(44, 700)
                                        ),
                                        new SlideComponent(
                                                "bullets",
                                                "bullets",
                                                null,
                                                List.of(
                                                        "AI tools often regenerate the whole slide.",
                                                        "Deterministic geometry is needed for precise edits."
                                                ),
                                                null,
                                                null,
                                                new Bounds(80, 160, 520, 260),
                                                new Style(28, null)
                                        )
                                )
                        ),
                        new SlideSpec(
                                "slide_2",
                                "Slide",
                                List.of(new SlideComponent(
                                        "title",
                                        "text",
                                        "Whole-slide regeneration causes drift",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 860, 80),
                                        new Style(44, 700)
                                ))
                        )
                )
        );
    }

    private static DeckInput imageDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(new SlideComponent(
                                "hero_image",
                                "image",
                                null,
                                null,
                                null,
                                null,
                                "A cinematic illustration of futuristic solar panels",
                                null,
                                "Solar panels",
                                new Bounds(80, 140, 640, 360),
                                null
                        ))
                ))
        );
    }

    private static DeckInput manualMatrixDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                new SlideComponent(
                                        "title",
                                        "text",
                                        "Manual matrix",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 600, 80),
                                        new Style(44, 700)
                                ),
                                new SlideComponent(
                                        "matrix-frame",
                                        "rect",
                                        null,
                                        null,
                                        null,
                                        null,
                                        new Bounds(160, 180, 800, 400),
                                        null
                                ),
                                new SlideComponent(
                                        "matrix-cell-1",
                                        "text",
                                        "A",
                                        null,
                                        null,
                                        null,
                                        new Bounds(180, 200, 200, 80),
                                        null
                                )
                        )
                ))
        );
    }

    private static DeckInput matrixDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                new SlideComponent(
                                        "title",
                                        "text",
                                        "Real matrix",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 600, 80),
                                        new Style(44, 700)
                                ),
                                new SlideComponent(
                                        "matrix",
                                        "matrix",
                                        null,
                                        null,
                                        null,
                                        List.of(List.of("A", "B"), List.of("C", "D")),
                                        new Bounds(160, 180, 800, 400),
                                        null
                                )
                        )
                ))
        );
    }

    private static DeckInput manualChartDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                new SlideComponent(
                                        "title",
                                        "text",
                                        "Manual chart",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 600, 80),
                                        new Style(44, 700)
                                ),
                                new SlideComponent(
                                        "bar-a",
                                        "rect",
                                        null,
                                        null,
                                        null,
                                        null,
                                        new Bounds(180, 420, 80, 120),
                                        null
                                ),
                                new SlideComponent(
                                        "bar-a-label",
                                        "text",
                                        "Team A",
                                        null,
                                        null,
                                        null,
                                        new Bounds(170, 560, 120, 40),
                                        null
                                )
                        )
                ))
        );
    }

    private static DeckInput chartDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                new SlideComponent(
                                        "title",
                                        "text",
                                        "Adoption rates",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 600, 80),
                                        new Style(44, 700)
                                ),
                                new SlideComponent(
                                        "adoption_chart",
                                        "chart",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        "bar",
                                        List.of("Team A", "Team B", "Team C"),
                                        List.of(30.0, 45.0, 60.0),
                                        new Bounds(160, 180, 800, 400),
                                        new Style(null, null, null, "#2563eb", "#374151", 2)
                                )
                        )
                ))
        );
    }

    private static DeckInput wrongDimensionMatrixDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                new SlideComponent(
                                        "title",
                                        "text",
                                        "Wrong matrix",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 600, 80),
                                        new Style(44, 700)
                                ),
                                new SlideComponent(
                                        "matrix-2x2",
                                        "matrix",
                                        null,
                                        null,
                                        null,
                                        List.of(
                                                List.of("User wants", "Style intent", "preserved"),
                                                List.of("AI outputs", "Plausible layout", "altered hierarchy"),
                                                List.of("Iteration prompt", "Overfits diffs", "not original goal"),
                                                List.of("Result", "Confusing slides", "more rework")
                                        ),
                                        new Bounds(160, 180, 800, 400),
                                        null
                                )
                        )
                ))
        );
    }

    private static DeckInput wrongHeaderCountTableDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                new SlideComponent(
                                        "title",
                                        "text",
                                        "Wrong table",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 600, 80),
                                        new Style(44, 700)
                                ),
                                new SlideComponent(
                                        "table",
                                        "table",
                                        null,
                                        null,
                                        List.of("Mode", "Behavior"),
                                        List.of(
                                                List.of("Free-form", "Drifts", "Needs rework"),
                                                List.of("Structured", "Stable", "Faster review")
                                        ),
                                        new Bounds(120, 160, 900, 360),
                                        null
                                )
                        )
                ))
        );
    }

    private static final class RecordingSlideAstClient implements SlideAstClient {
        private final List<DeckInput> responses;
        private final List<String> prompts = new ArrayList<>();

        private RecordingSlideAstClient(DeckInput firstResponse, DeckInput secondResponse) {
            this.responses = List.of(firstResponse, secondResponse);
        }

        @Override
        public DeckInput generateDeck(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }

        private List<String> prompts() {
            return prompts;
        }
    }

    private record ImageGenerationCall(String prompt, ImageGenerationOptions options) {
    }

    private static final class RecordingImageGeneratorClient implements ImageGeneratorClient {
        private final byte[] imageBytes;
        private final List<ImageGenerationCall> calls = new ArrayList<>();

        private RecordingImageGeneratorClient(byte[] imageBytes) {
            this.imageBytes = imageBytes;
        }

        @Override
        public byte[] generateImage(String prompt, ImageGenerationOptions options) {
            calls.add(new ImageGenerationCall(prompt, options));
            return imageBytes;
        }

        private List<ImageGenerationCall> calls() {
            return calls;
        }
    }
}
