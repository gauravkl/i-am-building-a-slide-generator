package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.RenderObject;
import com.example.slidegen.model.RenderedDeck;
import com.example.slidegen.model.RenderedSlidePage;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentDeckRendererTest {
    private final ComponentDeckRenderer renderer = new ComponentDeckRenderer();

    @Test
    void rendersCoreComponentsWithProvidedBounds() {
        DeckInput input = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(new SlideSpec(
                        "slide_1",
                        "Slide",
                        List.of(
                                component("title", "text", "Component deck", null, null, null, new Bounds(80, 48, 520, 70), new Style(44, 700)),
                                component("bullets", "bullets", null, List.of("One", "Two"), null, null, new Bounds(760, 170, 420, 240), new Style(28, null)),
                                component("rect", "rect", null, null, null, null, new Bounds(80, 160, 220, 120), new Style(null, null, null, "#e5e7eb", "#111827", 2)),
                                component("circle", "circle", null, null, null, null, new Bounds(340, 170, 90, 90), null),
                                component("arrow", "arrow", null, null, null, null, new Bounds(470, 200, 220, 40), new Style(null, null, null, null, "#111827", 3)),
                                component("matrix", "matrix", null, null, null, List.of(List.of("A", "B"), List.of("C", "D")), new Bounds(80, 330, 360, 220), null),
                                component("table", "table", null, null, List.of("Metric", "Value"), List.of(List.of("Speed", "High"), List.of("Control", "Precise")), new Bounds(500, 330, 520, 220), null),
                                new SlideComponent(
                                        "image",
                                        "image",
                                        null,
                                        null,
                                        null,
                                        null,
                                        "A clean architecture diagram",
                                        "generated-assets/images/slide_1-image-abc123.png",
                                        "Architecture diagram",
                                        new Bounds(1040, 330, 160, 120),
                                        null
                                ),
                                chart(
                                        "chart",
                                        List.of("A", "B", "C"),
                                        List.of(12.0, 24.0, 18.0),
                                        new Bounds(80, 580, 420, 110),
                                        new Style(null, null, null, "#2563eb", "#374151", 2)
                                )
                        )
                ))
        );

        RenderedDeck rendered = renderer.render(input);

        assertEquals("RenderedDeck", rendered.type());
        assertEquals(new SlideSize(1280, 720), rendered.size());
        assertEquals(1, rendered.slides().size());

        RenderedSlidePage slide = rendered.slides().get(0);
        assertEquals("slide_1", slide.id());
        assertEquals("RenderedSlide", slide.type());
        assertEquals(9, slide.objects().size());

        RenderObject title = slide.objects().get(0);
        assertEquals("text", title.type());
        assertEquals("Component deck", title.text());
        assertEquals(new Bounds(80, 48, 520, 70), title.bounds());

        RenderObject bullets = slide.objects().get(1);
        assertEquals("bullets", bullets.type());
        assertEquals(List.of("One", "Two"), bullets.items());

        RenderObject table = slide.objects().get(6);
        assertEquals("table", table.type());
        assertEquals(List.of("Metric", "Value"), table.headers());
        assertEquals(List.of(List.of("Speed", "High"), List.of("Control", "Precise")), table.rows());

        RenderObject image = slide.objects().get(7);
        assertEquals("image", image.type());
        assertEquals("A clean architecture diagram", image.imagePrompt());
        assertEquals("generated-assets/images/slide_1-image-abc123.png", image.src());
        assertEquals("Architecture diagram", image.alt());

        RenderObject chart = slide.objects().get(8);
        assertEquals("chart", chart.type());
        assertEquals("bar", chart.chartType());
        assertEquals(List.of("A", "B", "C"), chart.labels());
        assertEquals(List.of(12.0, 24.0, 18.0), chart.values());
    }

    @Test
    void rendersMultipleSlides() {
        DeckInput input = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(
                        slide("slide_1", component("title_1", "text", "First", null, null, null, new Bounds(80, 48, 400, 70), null)),
                        slide("slide_2", component("title_2", "text", "Second", null, null, null, new Bounds(80, 48, 400, 70), null))
                )
        );

        RenderedDeck rendered = renderer.render(input);

        assertEquals(2, rendered.slides().size());
        assertEquals("slide_1", rendered.slides().get(0).id());
        assertEquals("slide_2", rendered.slides().get(1).id());
    }

    @Test
    void rejectsMissingBounds() {
        DeckInput input = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", component("title", "text", "Title", null, null, null, null, null)))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(input));
    }

    @Test
    void rejectsOutOfSlideBounds() {
        DeckInput input = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", component("title", "text", "Title", null, null, null, new Bounds(1200, 40, 200, 80), null)))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(input));
    }

    @Test
    void rejectsUnsupportedType() {
        DeckInput input = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", component("video", "video", null, null, null, null, new Bounds(80, 80, 200, 120), null)))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(input));
    }

    @Test
    void rejectsMissingTypeSpecificContent() {
        DeckInput bulletsWithoutItems = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", component("bullets", "bullets", null, null, null, null, new Bounds(80, 80, 300, 200), null)))
        );
        DeckInput tableWithoutHeaders = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", component("table", "table", null, null, null, List.of(List.of("A")), new Bounds(80, 80, 300, 200), null)))
        );
        DeckInput imageWithoutSrc = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", new SlideComponent(
                        "image",
                        "image",
                        null,
                        null,
                        null,
                        null,
                        "A clean architecture diagram",
                        null,
                        "Architecture diagram",
                        new Bounds(80, 80, 300, 200),
                        null
                )))
        );
        DeckInput chartWithoutLabels = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", chart("chart", null, List.of(1.0), new Bounds(80, 80, 300, 200), null)))
        );
        DeckInput chartMismatchedValues = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", chart("chart", List.of("A", "B"), List.of(1.0), new Bounds(80, 80, 300, 200), null)))
        );
        DeckInput chartWithNegativeValue = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", chart("chart", List.of("A"), List.of(-1.0), new Bounds(80, 80, 300, 200), null)))
        );
        DeckInput unsupportedChartType = new DeckInput(
                "Deck",
                new SlideSize(1280, 720),
                List.of(slide("slide_1", new SlideComponent(
                        "chart",
                        "chart",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "line",
                        List.of("A"),
                        List.of(1.0),
                        new Bounds(80, 80, 300, 200),
                        null
                )))
        );

        assertThrows(SlideLayoutException.class, () -> renderer.render(bulletsWithoutItems));
        assertThrows(SlideLayoutException.class, () -> renderer.render(tableWithoutHeaders));
        assertThrows(SlideLayoutException.class, () -> renderer.render(imageWithoutSrc));
        assertThrows(SlideLayoutException.class, () -> renderer.render(chartWithoutLabels));
        assertThrows(SlideLayoutException.class, () -> renderer.render(chartMismatchedValues));
        assertThrows(SlideLayoutException.class, () -> renderer.render(chartWithNegativeValue));
        assertThrows(SlideLayoutException.class, () -> renderer.render(unsupportedChartType));
    }

    private static SlideSpec slide(String id, SlideComponent component) {
        return new SlideSpec(id, "Slide", List.of(component));
    }

    private static SlideComponent component(
            String id,
            String type,
            String text,
            List<String> items,
            List<String> headers,
            List<List<String>> rows,
            Bounds bounds,
            Style style
    ) {
        return new SlideComponent(id, type, text, items, headers, rows, bounds, style);
    }

    private static SlideComponent chart(String id, List<String> labels, List<Double> values, Bounds bounds, Style style) {
        return new SlideComponent(
                id,
                "chart",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "bar",
                labels,
                values,
                bounds,
                style
        );
    }
}
