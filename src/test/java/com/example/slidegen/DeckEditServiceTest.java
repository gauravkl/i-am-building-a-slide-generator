package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.DeckEditOperation;
import com.example.slidegen.model.DeckEditPatch;
import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeckEditServiceTest {
    @Test
    void appliesPatchThatSwapsTextAndCircleBoundsWithoutChangingContent() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(
                        updateBounds("slide_2", "label", new Bounds(520, 180, 160, 160)),
                        updateBounds("slide_2", "shape", new Bounds(120, 180, 260, 80))
                )
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(swappableDeck(), "swap the text and circle in slide 2");

        SlideComponent text = edited.slides().get(1).components().get(0);
        SlideComponent circle = edited.slides().get(1).components().get(1);
        assertEquals("text", text.type());
        assertEquals("Circle label", text.text());
        assertEquals(new Bounds(520, 180, 160, 160), text.bounds());
        assertEquals(new Style(30, 700), text.style());
        assertEquals("circle", circle.type());
        assertEquals(new Bounds(120, 180, 260, 80), circle.bounds());
        assertEquals(new Style(null, null, null, "#93c5fd", "#1d4ed8", 3), circle.style());
        assertEquals(List.of("swap the text and circle in slide 2"), client.instructions());
    }

    @Test
    void appliesPatchThatChangesRectangleToRedCircleAndPreservesStroke() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation(
                        "updateComponent",
                        "slide_2",
                        "shape",
                        "circle",
                        new Bounds(140, 160, 160, 160),
                        new Style(null, null, null, "#ef4444", null, null)
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(editableDeck(), "change rectangle in slide 2 to red circle");

        SlideComponent shape = edited.slides().get(1).components().get(0);
        assertEquals("circle", shape.type());
        assertEquals(new Bounds(140, 160, 160, 160), shape.bounds());
        assertEquals(new Style(null, null, null, "#ef4444", "#1d4ed8", 3), shape.style());
        assertEquals("Slide two label", edited.slides().get(1).components().get(1).text());
    }

    @Test
    void appliesPatchToChartBoundsAndStyleWithoutChangingChartData() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation(
                        "updateComponent",
                        "slide_2",
                        "chart",
                        null,
                        new Bounds(120, 200, 680, 320),
                        new Style(null, null, null, "#22c55e", null, null)
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(chartDeck(), "make the chart larger and green in slide 2");

        SlideComponent chart = edited.slides().get(1).components().get(0);
        assertEquals("chart", chart.type());
        assertEquals("bar", chart.chartType());
        assertEquals(List.of("A", "B", "C"), chart.labels());
        assertEquals(List.of(10.0, 20.0, 30.0), chart.values());
        assertEquals(new Bounds(120, 200, 680, 320), chart.bounds());
        assertEquals(new Style(null, null, null, "#22c55e", "#374151", 2), chart.style());
    }

    @Test
    void appliesPatchThatChangesBulletsToTwoByTwoMatrix() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
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
                        new Bounds(120, 150, 620, 360),
                        null
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(contentDeck(), "change bullets in slide 1 to a 2 by 2 matrix");

        SlideComponent matrix = edited.slides().get(0).components().get(1);
        assertEquals("matrix", matrix.type());
        assertEquals(List.of(List.of("Plan", "Build"), List.of("Launch", "Learn")), matrix.rows());
        assertEquals(null, matrix.items());
        assertEquals(null, matrix.headers());
        assertEquals(new Bounds(120, 150, 620, 360), matrix.bounds());
    }

    @Test
    void appliesPatchThatChangesBulletsToThreeColumnTable() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation(
                        "updateComponent",
                        "slide_1",
                        "bullets",
                        "table",
                        null,
                        null,
                        List.of("Focus", "Owner", "Status"),
                        List.of(
                                List.of("Plan scope", "PM", "Ready"),
                                List.of("Build release", "Engineering", "Active")
                        ),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new Bounds(100, 150, 760, 300),
                        null
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(contentDeck(), "change bullets in slide 1 to a 3 column table");

        SlideComponent table = edited.slides().get(0).components().get(1);
        assertEquals("table", table.type());
        assertEquals(List.of("Focus", "Owner", "Status"), table.headers());
        assertEquals(List.of(
                List.of("Plan scope", "PM", "Ready"),
                List.of("Build release", "Engineering", "Active")
        ), table.rows());
        assertEquals(null, table.items());
    }

    @Test
    void appliesPatchThatUpdatesTextContentWithoutChangingBoundsOrStyle() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation(
                        "updateComponent",
                        "slide_1",
                        "title",
                        null,
                        "Updated title",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(contentDeck(), "change the title text");

        SlideComponent title = edited.slides().get(0).components().get(0);
        assertEquals("text", title.type());
        assertEquals("Updated title", title.text());
        assertEquals(new Bounds(80, 48, 720, 80), title.bounds());
        assertEquals(new Style(44, 700), title.style());
    }

    @Test
    void appliesPatchThatUpdatesChartData() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation(
                        "updateComponent",
                        "slide_2",
                        "chart",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of("North", "South"),
                        List.of(14.0, 28.0),
                        null,
                        null
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(chartDeck(), "update the chart data");

        SlideComponent chart = edited.slides().get(1).components().get(0);
        assertEquals("chart", chart.type());
        assertEquals("bar", chart.chartType());
        assertEquals(List.of("North", "South"), chart.labels());
        assertEquals(List.of(14.0, 28.0), chart.values());
    }

    @Test
    void rejectsPatchTargetingMissingComponent() {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(updateBounds("slide_2", "missing", new Bounds(520, 180, 160, 160)))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        SlideLayoutException ex = assertThrows(
                SlideLayoutException.class,
                () -> service.edit(swappableDeck(), "swap the text and circle in slide 2")
        );

        assertEquals("Edit patch targets missing component: slide_2/missing", ex.getMessage());
    }

    @Test
    void rejectsPatchWithDuplicateOperations() {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(
                        updateBounds("slide_2", "label", new Bounds(520, 180, 160, 160)),
                        updateBounds("slide_2", "label", new Bounds(600, 180, 160, 160))
                )
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        SlideLayoutException ex = assertThrows(
                SlideLayoutException.class,
                () -> service.edit(swappableDeck(), "swap the text and circle in slide 2")
        );

        assertEquals("Edit patch contains duplicate operations for component: slide_2/label", ex.getMessage());
    }

    @Test
    void retriesOnceWhenPatchCreatesInvalidBounds() throws Exception {
        RecordingSlideEditClient client = new RecordingSlideEditClient(
                new DeckEditPatch(
                        "DeckEditPatch",
                        List.of(updateBounds("slide_2", "label", new Bounds(1200, 180, 200, 80)))
                ),
                new DeckEditPatch(
                        "DeckEditPatch",
                        List.of(updateBounds("slide_2", "label", new Bounds(900, 180, 200, 80)))
                )
        );
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        DeckInput edited = service.edit(swappableDeck(), "move the text in slide 2 to the right");

        assertEquals(new Bounds(900, 180, 200, 80), edited.slides().get(1).components().get(0).bounds());
        assertEquals(2, client.instructions().size());
        assertTrue(client.instructions().get(1).contains("Correction from the Java edit patch validator"));
        assertTrue(client.instructions().get(1).contains("Component bounds exceed slide size"));
    }

    @Test
    void rejectsPatchWithNoActualUpdates() {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation("updateComponent", "slide_2", "label", null, null, null))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        SlideLayoutException ex = assertThrows(
                SlideLayoutException.class,
                () -> service.edit(swappableDeck(), "edit text")
        );

        assertEquals("Edit operation must update type, bounds, style, or content: slide_2/label", ex.getMessage());
    }

    @Test
    void rejectsMatrixConversionWithoutRows() {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation("updateComponent", "slide_1", "bullets", "matrix", null, null))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        SlideLayoutException ex = assertThrows(
                SlideLayoutException.class,
                () -> service.edit(contentDeck(), "change bullets to matrix")
        );

        assertEquals("Component requires rows: bullets", ex.getMessage());
    }

    @Test
    void rejectsTableConversionWithMismatchedRows() {
        RecordingSlideEditClient client = new RecordingSlideEditClient(new DeckEditPatch(
                "DeckEditPatch",
                List.of(new DeckEditOperation(
                        "updateComponent",
                        "slide_1",
                        "bullets",
                        "table",
                        null,
                        null,
                        List.of("One", "Two", "Three"),
                        List.of(List.of("A", "B")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        ));
        DeckEditService service = new DeckEditService(client, new ComponentDeckRenderer());

        SlideLayoutException ex = assertThrows(
                SlideLayoutException.class,
                () -> service.edit(contentDeck(), "change bullets to table")
        );

        assertEquals("Table headers must match row column count: bullets", ex.getMessage());
    }

    private static DeckEditOperation updateBounds(String slideId, String componentId, Bounds bounds) {
        return new DeckEditOperation("updateComponent", slideId, componentId, null, bounds, null);
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

    private static DeckInput chartDeck() {
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
                                List.of(new SlideComponent(
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
                                        List.of(10.0, 20.0, 30.0),
                                        new Bounds(100, 180, 520, 260),
                                        new Style(null, null, null, "#2563eb", "#374151", 2)
                                ))
                        )
                )
        );
    }

    private static DeckInput contentDeck() {
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
                                        "Release planning",
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
                                        List.of("Plan scope", "Build release", "Launch safely", "Learn quickly"),
                                        null,
                                        null,
                                        new Bounds(80, 160, 520, 260),
                                        new Style(28, null)
                                )
                        )
                ))
        );
    }

    private static final class RecordingSlideEditClient implements SlideEditClient {
        private final List<DeckEditPatch> patches;
        private final List<String> instructions = new ArrayList<>();

        private RecordingSlideEditClient(DeckEditPatch... patches) {
            this.patches = List.of(patches);
        }

        @Override
        public DeckEditPatch generatePatch(DeckInput deckInput, String instruction) {
            instructions.add(instruction);
            return patches.get(Math.min(instructions.size() - 1, patches.size() - 1));
        }

        private List<String> instructions() {
            return instructions;
        }
    }
}
