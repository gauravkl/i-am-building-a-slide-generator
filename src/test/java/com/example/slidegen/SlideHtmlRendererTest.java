package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.RenderObject;
import com.example.slidegen.model.RenderedDeck;
import com.example.slidegen.model.RenderedSlidePage;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlideHtmlRendererTest {
    private final SlideHtmlRenderer renderer = new SlideHtmlRenderer();

    @Test
    void rendersStackedSlidesAndCoreObjectsAsHtml() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(
                        new RenderedSlidePage(
                                "slide_1",
                                "RenderedSlide",
                                List.of(
                                        new RenderObject("title", "text", "Why AI slide iteration breaks", new Bounds(80, 48, 520, 80), new Style(44, 700)),
                                        new RenderObject("bullets", "bullets", null, List.of("One", "Two"), null, null, new Bounds(760, 170, 420, 240), new Style(28, null)),
                                        new RenderObject("rect", "rect", null, new Bounds(80, 160, 220, 120), new Style(null, null, null, "#e5e7eb", "#111827", 2)),
                                        new RenderObject("circle", "circle", null, new Bounds(340, 170, 90, 90), null),
                                        new RenderObject("arrow", "arrow", null, new Bounds(470, 200, 220, 40), new Style(null, null, null, null, "#111827", 3)),
                                        new RenderObject("matrix", "matrix", null, null, null, List.of(List.of("A", "B"), List.of("C", "D")), new Bounds(80, 330, 360, 220), null),
                                        new RenderObject("table", "table", null, null, List.of("Metric", "Value"), List.of(List.of("Speed", "High"), List.of("Control", "Precise")), new Bounds(500, 330, 520, 220), null),
                                        new RenderObject(
                                                "hero_image",
                                                "image",
                                                null,
                                                null,
                                                null,
                                                null,
                                                "A clean architecture diagram",
                                                "generated-assets/images/slide_1-hero_image-abc123.png",
                                                "Architecture diagram",
                                                new Bounds(1040, 330, 160, 120),
                                                null
                                        ),
                                        new RenderObject(
                                                "chart",
                                                "chart",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                "bar",
                                                List.of("A", "B", "C"),
                                                List.of(12.0, 24.0, 18.0),
                                                new Bounds(80, 580, 420, 110),
                                                new Style(null, null, null, "#22c55e", "#111827", 2)
                                        )
                                )
                        )
                )
        );

        String html = renderer.render(deck);

        assertTrue(html.contains("<!doctype html>"));
        assertTrue(html.contains("<main class=\"deck\">"));
        assertTrue(html.contains("<section class=\"slide\" data-slide-id=\"slide_1\" style=\"width: 1280px; height: 720px;\">"));
        assertTrue(html.contains("class=\"object text\" data-object-id=\"title\""));
        assertTrue(html.contains("left: 80px; top: 48px; width: 520px; min-height: 80px; font-size: 44px; font-weight: 700;"));
        assertTrue(html.contains(">Why AI slide iteration breaks</div>"));
        assertTrue(html.contains("class=\"object bullets\" data-object-id=\"bullets\""));
        assertTrue(html.contains("<li>One</li>"));
        assertTrue(html.contains("class=\"object rect\" data-object-id=\"rect\""));
        assertTrue(html.contains("class=\"object circle\" data-object-id=\"circle\""));
        assertTrue(html.contains("class=\"object arrow\" data-object-id=\"arrow\""));
        assertTrue(html.contains("class=\"arrow-line\""));
        assertTrue(html.contains("class=\"object matrix\" data-object-id=\"matrix\""));
        assertTrue(html.contains("<td>A</td>"));
        assertTrue(html.contains("class=\"object table\" data-object-id=\"table\""));
        assertTrue(html.contains("<th>Metric</th>"));
        assertTrue(html.contains(".image img { width: 100%; height: 100%; display: block; object-fit: contain; }"));
        assertTrue(html.contains("class=\"object image\" data-object-id=\"hero_image\""));
        assertTrue(html.contains("<img src=\"generated-assets/images/slide_1-hero_image-abc123.png\" alt=\"Architecture diagram\">"));
        assertTrue(html.contains(".chart svg { width: 100%; height: 100%; display: block; }"));
        assertTrue(html.contains("class=\"object chart\" data-object-id=\"chart\""));
        assertTrue(html.contains("<svg role=\"img\" viewBox=\"0 0 420 110\" aria-label=\"Bar chart\">"));
        assertTrue(html.contains("fill=\"#22c55e\""));
        assertTrue(html.contains(">24</text>"));
        assertTrue(html.contains(">B</text>"));
    }

    @Test
    void escapesTextAndAttributes() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(400, 300),
                List.of(new RenderedSlidePage(
                        "slide\"bad",
                        "RenderedSlide",
                        List.of(new RenderObject(
                                "title\"bad",
                                "text",
                                "A <tag> & value",
                                new Bounds(1, 2, 3, 4),
                                null
                        ))
                ))
        );

        String html = renderer.render(deck);

        assertTrue(html.contains("data-slide-id=\"slide&quot;bad\""));
        assertTrue(html.contains("data-object-id=\"title&quot;bad\""));
        assertTrue(html.contains(">A &lt;tag&gt; &amp; value</div>"));
    }

    @Test
    void ignoresFillColorOnTextAndBulletsSoContentStaysReadable() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(
                                new RenderObject(
                                        "title",
                                        "text",
                                        "Readable title",
                                        null,
                                        null,
                                        null,
                                        new Bounds(60, 40, 500, 60),
                                        new Style(34, 700, null, "#111111", null, null)
                                ),
                                new RenderObject(
                                        "bullets",
                                        "bullets",
                                        null,
                                        List.of("Readable bullet"),
                                        null,
                                        null,
                                        new Bounds(60, 120, 500, 120),
                                        new Style(22, null, null, "#0F172A", null, null)
                                )
                        )
                ))
        );

        String html = renderer.render(deck);

        assertTrue(html.contains("data-object-id=\"title\" style=\"left: 60px; top: 40px; width: 500px; min-height: 60px; font-size: 34px; font-weight: 700;\">Readable title"));
        assertTrue(html.contains("data-object-id=\"bullets\" style=\"left: 60px; top: 120px; width: 500px; height: 120px; font-size: 22px;\"><ul><li>Readable bullet</li></ul>"));
    }

    @Test
    void normalizesWhiteTextAndBulletsToDefaultDarkText() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(
                                new RenderObject(
                                        "label",
                                        "text",
                                        "Launch",
                                        null,
                                        null,
                                        null,
                                        new Bounds(970, 198, 220, 55),
                                        new Style(34, 700, "#FFFFFF", null, null, null)
                                ),
                                new RenderObject(
                                        "bullets",
                                        "bullets",
                                        null,
                                        List.of("Readable"),
                                        null,
                                        null,
                                        new Bounds(60, 120, 500, 120),
                                        new Style(22, null, "white", null, null, null)
                                )
                        )
                ))
        );

        String html = renderer.render(deck);

        assertTrue(html.contains("data-object-id=\"label\" style=\"left: 970px; top: 198px; width: 220px; min-height: 55px; font-size: 34px; font-weight: 700; color: #111827;\">Launch"));
        assertTrue(html.contains("data-object-id=\"bullets\" style=\"left: 60px; top: 120px; width: 500px; height: 120px; font-size: 22px; color: #111827;\"><ul><li>Readable</li></ul>"));
    }

    @Test
    void allowsPlainTextToOverflowSoTightShapeLabelsRemainReadable() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(new RenderObject(
                                "circle_label",
                                "text",
                                "LLM AST",
                                new Bounds(455, 318, 85, 36),
                                new Style(22, 700)
                        ))
                ))
        );

        String html = renderer.render(deck);

        assertTrue(html.contains(".text { overflow: visible; overflow-wrap: anywhere; }"));
        assertTrue(html.contains(".bullets { overflow: hidden; }"));
        assertTrue(html.contains("data-object-id=\"circle_label\" style=\"left: 455px; top: 318px; width: 85px; min-height: 36px; font-size: 22px; font-weight: 700;\">LLM AST"));
        assertTrue(html.contains(">LLM AST</div>"));
    }

    @Test
    void rejectsNonRenderedDeckInput() {
        RenderedDeck deck = new RenderedDeck("RenderedSlide", new SlideSize(1280, 720), List.of());

        assertThrows(SlideLayoutException.class, () -> renderer.render(deck));
    }

    @Test
    void rejectsImageWithoutSrc() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(new RenderObject(
                                "image_1",
                                "image",
                                null,
                                null,
                                null,
                                null,
                                "A clean architecture diagram",
                                null,
                                "Architecture diagram",
                                new Bounds(0, 0, 100, 100),
                                null
                        ))
                ))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(deck));
    }

    @Test
    void rendersChartLabelsSafely() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(new RenderObject(
                                "chart_1",
                                "chart",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "bar",
                                List.of("A < B", "C & D"),
                                List.of(10.0, 20.0),
                                new Bounds(80, 80, 400, 240),
                                null
                        ))
                ))
        );

        String html = renderer.render(deck);

        assertTrue(html.contains(">A &lt; B</text>"));
        assertTrue(html.contains(">C &amp; D</text>"));
    }

    @Test
    void rejectsInvalidChartObjects() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(new RenderObject(
                                "chart_1",
                                "chart",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "bar",
                                List.of("A", "B"),
                                List.of(10.0),
                                new Bounds(80, 80, 400, 240),
                                null
                        ))
                ))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(deck));
    }

    @Test
    void rejectsUnsupportedObjectType() {
        RenderedDeck deck = new RenderedDeck(
                "RenderedDeck",
                new SlideSize(1280, 720),
                List.of(new RenderedSlidePage(
                        "slide_1",
                        "RenderedSlide",
                        List.of(new RenderObject("video_1", "video", null, new Bounds(0, 0, 100, 100), null))
                ))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(deck));
    }
}
