package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.DeckEditOperation;
import com.example.slidegen.model.DeckEditPatch;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideGeneratorAppTest {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    @Test
    void promptModeWritesSampleSlideAndPrintsDeckAst() throws Exception {
        CliResult result = runCli(
                new String[]{"--prompt", "create a slide"},
                () -> prompt -> sampleDeck()
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"type\" : \"Deck\""));
        assertTrue(Files.readString(tempDir.resolve("sample-slide.json")).contains("\"text\" : \"Why AI slide iteration breaks\""));
        assertFalse(Files.exists(tempDir.resolve("rendered-slide.json")));
        assertFalse(Files.exists(tempDir.resolve("slide.html")));
    }

    @Test
    void promptToHtmlModeWritesAllFlowFiles() throws Exception {
        CliResult result = runCli(
                new String[]{"--prompt-to-html", "create a slide"},
                () -> prompt -> sampleDeck()
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"slides\""));
        assertTrue(Files.readString(tempDir.resolve("sample-slide.json")).contains("\"type\" : \"Deck\""));
        assertTrue(Files.readString(tempDir.resolve("rendered-slide.json")).contains("\"type\" : \"RenderedDeck\""));
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("<main class=\"deck\">"));
    }

    @Test
    void missingApiKeyPathReturnsNonZeroAndDoesNotWriteFiles() throws Exception {
        CliResult result = runCli(
                new String[]{"--prompt", "create a slide"},
                () -> {
                    throw new SlideLayoutException("OPENAI_API_KEY is required for --prompt and --prompt-to-html.");
                }
        );

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("OPENAI_API_KEY"));
        assertFalse(Files.exists(tempDir.resolve("sample-slide.json")));
    }

    @Test
    void editToHtmlModeUpdatesDeckAndWritesRenderArtifacts() throws Exception {
        Path samplePath = tempDir.resolve("sample-slide.json");
        MAPPER.writeValue(samplePath.toFile(), editableDeck());

        CliResult result = runCliWithEdit(
                new String[]{"--edit-to-html", "change rectangle in slide 2 to red circle"},
                () -> new RecordingSlideEditClient(new DeckEditPatch(
                        "DeckEditPatch",
                        List.of(new DeckEditOperation(
                                "updateComponent",
                                "slide_2",
                                "shape",
                                "circle",
                                new Bounds(140, 160, 160, 160),
                                new Style(null, null, null, "#ef4444", null, null)
                        ))
                ))
        );

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"type\" : \"circle\""));

        DeckInput edited = MAPPER.readValue(samplePath.toFile(), DeckInput.class);
        SlideComponent editedShape = edited.slides().get(1).components().get(0);
        assertEquals("circle", editedShape.type());
        assertEquals(new Bounds(140, 160, 160, 160), editedShape.bounds());
        assertEquals("#ef4444", editedShape.style().fillColor());
        assertEquals("#1d4ed8", editedShape.style().strokeColor());
        assertEquals("Slide two label", edited.slides().get(1).components().get(1).text());
        assertTrue(Files.readString(tempDir.resolve("rendered-slide.json")).contains("\"type\" : \"RenderedDeck\""));
        assertTrue(Files.readString(tempDir.resolve("rendered-slide.json")).contains("\"fillColor\" : \"#ef4444\""));
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("class=\"object circle\" data-object-id=\"shape\""));
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("background: #ef4444;"));
    }

    @Test
    void editToHtmlModeSwapsTextAndCircleBounds() throws Exception {
        Path samplePath = tempDir.resolve("sample-slide.json");
        MAPPER.writeValue(samplePath.toFile(), swappableDeck());

        CliResult result = runCliWithEdit(
                new String[]{"--edit-to-html", "swap the text and circle in slide 2"},
                () -> new RecordingSlideEditClient(new DeckEditPatch(
                        "DeckEditPatch",
                        List.of(
                                new DeckEditOperation("updateComponent", "slide_2", "label", null, new Bounds(520, 180, 160, 160), null),
                                new DeckEditOperation("updateComponent", "slide_2", "shape", null, new Bounds(120, 180, 260, 80), null)
                        )
                ))
        );

        assertEquals(0, result.exitCode());

        DeckInput edited = MAPPER.readValue(samplePath.toFile(), DeckInput.class);
        SlideComponent text = edited.slides().get(1).components().get(0);
        SlideComponent circle = edited.slides().get(1).components().get(1);
        assertEquals("text", text.type());
        assertEquals("Circle label", text.text());
        assertEquals(new Bounds(520, 180, 160, 160), text.bounds());
        assertEquals("circle", circle.type());
        assertEquals(new Bounds(120, 180, 260, 80), circle.bounds());
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("data-object-id=\"label\" style=\"left: 520px; top: 180px; width: 160px;"));
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("data-object-id=\"shape\" style=\"left: 120px; top: 180px; width: 260px;"));
    }

    @Test
    void editToHtmlModeConvertsBulletsToMatrixAndWritesArtifacts() throws Exception {
        Path samplePath = tempDir.resolve("sample-slide.json");
        MAPPER.writeValue(samplePath.toFile(), sampleDeck());

        CliResult result = runCliWithEdit(
                new String[]{"--edit-to-html", "change bullets in slide 1 to a 2 by 2 matrix"},
                () -> new RecordingSlideEditClient(new DeckEditPatch(
                        "DeckEditPatch",
                        List.of(new DeckEditOperation(
                                "updateComponent",
                                "slide_1",
                                "bullets",
                                "matrix",
                                null,
                                null,
                                null,
                                List.of(List.of("Plan", "Build"), List.of("Launch", "Learn")),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                new Bounds(80, 160, 520, 260),
                                null
                        ))
                ))
        );

        assertEquals(0, result.exitCode());
        DeckInput edited = MAPPER.readValue(samplePath.toFile(), DeckInput.class);
        SlideComponent matrix = edited.slides().get(0).components().get(1);
        assertEquals("matrix", matrix.type());
        assertEquals(List.of(List.of("Plan", "Build"), List.of("Launch", "Learn")), matrix.rows());
        assertEquals(null, matrix.items());
        assertTrue(Files.readString(tempDir.resolve("rendered-slide.json")).contains("\"type\" : \"matrix\""));
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("class=\"object matrix\" data-object-id=\"bullets\""));
        assertTrue(Files.readString(tempDir.resolve("slide.html")).contains("<td>Plan</td>"));
    }

    @Test
    void editToHtmlMissingApiKeyReturnsNonZeroAndDoesNotWriteArtifacts() throws Exception {
        Path samplePath = tempDir.resolve("sample-slide.json");
        DeckInput original = swappableDeck();
        MAPPER.writeValue(samplePath.toFile(), original);

        CliResult result = runCliWithEdit(
                new String[]{"--edit-to-html", "swap the text and circle in slide 2"},
                () -> {
                    throw new SlideLayoutException("OPENAI_API_KEY is required for --edit-to-html.");
                }
        );

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("OPENAI_API_KEY"));
        assertEquals(original, MAPPER.readValue(samplePath.toFile(), DeckInput.class));
        assertFalse(Files.exists(tempDir.resolve("rendered-slide.json")));
        assertFalse(Files.exists(tempDir.resolve("slide.html")));
    }

    @Test
    void editToHtmlInvalidPatchDoesNotOverwriteFiles() throws Exception {
        Path samplePath = tempDir.resolve("sample-slide.json");
        DeckInput original = swappableDeck();
        MAPPER.writeValue(samplePath.toFile(), original);

        CliResult result = runCliWithEdit(
                new String[]{"--edit-to-html", "swap the text and circle in slide 2"},
                () -> new RecordingSlideEditClient(new DeckEditPatch(
                        "DeckEditPatch",
                        List.of(new DeckEditOperation("updateComponent", "slide_2", "missing", null, new Bounds(520, 180, 160, 160), null))
                ))
        );

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("missing component"));
        assertEquals(original, MAPPER.readValue(samplePath.toFile(), DeckInput.class));
        assertFalse(Files.exists(tempDir.resolve("rendered-slide.json")));
        assertFalse(Files.exists(tempDir.resolve("slide.html")));
    }

    private CliResult runCli(String[] args, SlideGeneratorApp.SlideAstClientFactory factory) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            int exitCode = SlideGeneratorApp.run(args, factory, tempDir);
            return new CliResult(exitCode, stdout.toString(), stderr.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private CliResult runCliWithEdit(String[] args, SlideGeneratorApp.SlideEditClientFactory editFactory) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(stdout));
            System.setErr(new PrintStream(stderr));
            int exitCode = SlideGeneratorApp.run(
                    args,
                    () -> prompt -> {
                        throw new SlideLayoutException("OpenAI slide generation should not be called for edit tests.");
                    },
                    ImageGeneratorClient::disabled,
                    editFactory,
                    tempDir,
                    Path.of("generated-assets/images"),
                    OpenAiImageGeneratorClient.DEFAULT_MODEL,
                    OpenAiImageGeneratorClient.DEFAULT_QUALITY
            );
            return new CliResult(exitCode, stdout.toString(), stderr.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static DeckInput sampleDeck() {
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
                ))
        );
    }

    private static DeckInput editableDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(
                        new SlideSpec(
                                "slide_1",
                                "Slide",
                                List.of(new SlideComponent(
                                        "title",
                                        "text",
                                        "First slide",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 720, 80),
                                        new Style(44, 700)
                                ))
                        ),
                        new SlideSpec(
                                "slide_2",
                                "Slide",
                                List.of(
                                        new SlideComponent(
                                                "shape",
                                                "rect",
                                                null,
                                                null,
                                                null,
                                                null,
                                                new Bounds(100, 160, 240, 160),
                                                new Style(null, null, null, "#93c5fd", "#1d4ed8", 3)
                                        ),
                                        new SlideComponent(
                                                "label",
                                                "text",
                                                "Slide two label",
                                                null,
                                                null,
                                                null,
                                                new Bounds(380, 160, 300, 80),
                                                new Style(30, 700)
                                        )
                                )
                        )
                )
        );
    }

    private static DeckInput swappableDeck() {
        return new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(
                        new SlideSpec(
                                "slide_1",
                                "Slide",
                                List.of(new SlideComponent(
                                        "title",
                                        "text",
                                        "First slide",
                                        null,
                                        null,
                                        null,
                                        new Bounds(80, 48, 720, 80),
                                        new Style(44, 700)
                                ))
                        ),
                        new SlideSpec(
                                "slide_2",
                                "Slide",
                                List.of(
                                        new SlideComponent(
                                                "label",
                                                "text",
                                                "Circle label",
                                                null,
                                                null,
                                                null,
                                                new Bounds(120, 180, 260, 80),
                                                new Style(30, 700)
                                        ),
                                        new SlideComponent(
                                                "shape",
                                                "circle",
                                                null,
                                                null,
                                                null,
                                                null,
                                                new Bounds(520, 180, 160, 160),
                                                new Style(null, null, null, "#93c5fd", "#1d4ed8", 3)
                                        )
                                )
                        )
                )
        );
    }

    private record CliResult(int exitCode, String stdout, String stderr) {
    }

    private record RecordingSlideEditClient(DeckEditPatch patch) implements SlideEditClient {
        @Override
        public DeckEditPatch generatePatch(DeckInput deckInput, String instruction) {
            return patch;
        }
    }
}
