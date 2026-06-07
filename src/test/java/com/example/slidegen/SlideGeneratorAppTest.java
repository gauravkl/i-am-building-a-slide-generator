package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;
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

    private record CliResult(int exitCode, String stdout, String stderr) {
    }
}
