package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.RenderObject;
import com.example.slidegen.model.RenderedDeck;
import com.example.slidegen.model.RenderedSlidePage;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ComponentDeckRenderer {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "text",
            "bullets",
            "rect",
            "circle",
            "arrow",
            "matrix",
            "table"
    );

    public RenderedDeck render(DeckInput input) {
        validateDeck(input);

        List<RenderedSlidePage> renderedSlides = new ArrayList<>();
        for (SlideSpec slide : input.slides()) {
            renderedSlides.add(renderSlide(input.size(), slide));
        }

        return new RenderedDeck("RenderedDeck", input.size(), List.copyOf(renderedSlides));
    }

    private static RenderedSlidePage renderSlide(SlideSize size, SlideSpec slide) {
        validateSlide(slide);

        List<RenderObject> objects = new ArrayList<>();
        for (SlideComponent component : slide.components()) {
            validateComponent(size, component);
            objects.add(new RenderObject(
                    component.id(),
                    component.type(),
                    component.text(),
                    component.items(),
                    component.headers(),
                    component.rows(),
                    component.bounds(),
                    component.style()
            ));
        }

        return new RenderedSlidePage(slide.id(), "RenderedSlide", List.copyOf(objects));
    }

    private static void validateDeck(DeckInput input) {
        if (input == null) {
            throw new SlideLayoutException("Input JSON must describe a deck.");
        }
        if (!"Deck".equals(input.type())) {
            throw new SlideLayoutException("Expected type to be 'Deck'.");
        }
        if (input.size() == null) {
            throw new SlideLayoutException("Deck size is required.");
        }
        if (input.size().width() <= 0 || input.size().height() <= 0) {
            throw new SlideLayoutException("Deck size width and height must be positive.");
        }
        if (input.slides() == null || input.slides().isEmpty()) {
            throw new SlideLayoutException("Deck must contain at least one slide.");
        }
    }

    private static void validateSlide(SlideSpec slide) {
        if (slide == null) {
            throw new SlideLayoutException("Deck slides cannot contain null entries.");
        }
        if (slide.id() == null || slide.id().isBlank()) {
            throw new SlideLayoutException("Slide id is required.");
        }
        if (!"Slide".equals(slide.type())) {
            throw new SlideLayoutException("Expected slide type to be 'Slide'.");
        }
        if (slide.components() == null || slide.components().isEmpty()) {
            throw new SlideLayoutException("Slide must contain at least one component: " + slide.id());
        }
    }

    private static void validateComponent(SlideSize size, SlideComponent component) {
        if (component == null) {
            throw new SlideLayoutException("Slide components cannot contain null entries.");
        }
        if (component.id() == null || component.id().isBlank()) {
            throw new SlideLayoutException("Component id is required.");
        }
        if (!SUPPORTED_TYPES.contains(component.type())) {
            throw new SlideLayoutException("Unsupported component type: " + component.type());
        }
        validateBounds(size, component.id(), component.bounds());

        switch (component.type()) {
            case "text" -> requireText(component);
            case "bullets" -> requireItems(component);
            case "matrix" -> requireRows(component);
            case "table" -> {
                requireHeaders(component);
                requireRows(component);
            }
            case "rect", "circle", "arrow" -> {
                // Shape components only require id/type/bounds.
            }
            default -> throw new SlideLayoutException("Unsupported component type: " + component.type());
        }
    }

    private static void validateBounds(SlideSize size, String componentId, Bounds bounds) {
        if (bounds == null) {
            throw new SlideLayoutException("Component bounds are required: " + componentId);
        }
        if (bounds.w() <= 0 || bounds.h() <= 0) {
            throw new SlideLayoutException("Component bounds must have positive width and height: " + componentId);
        }
        if (bounds.x() < 0 || bounds.y() < 0) {
            throw new SlideLayoutException("Component bounds must be inside the slide: " + componentId);
        }
        if (bounds.x() + bounds.w() > size.width() || bounds.y() + bounds.h() > size.height()) {
            throw new SlideLayoutException("Component bounds exceed slide size: " + componentId);
        }
    }

    private static void requireText(SlideComponent component) {
        if (component.text() == null || component.text().isBlank()) {
            throw new SlideLayoutException("Text component requires text: " + component.id());
        }
    }

    private static void requireItems(SlideComponent component) {
        if (component.items() == null || component.items().isEmpty()) {
            throw new SlideLayoutException("Bullets component requires items: " + component.id());
        }
        for (String item : component.items()) {
            if (item == null || item.isBlank()) {
                throw new SlideLayoutException("Bullets component items cannot be blank: " + component.id());
            }
        }
    }

    private static void requireHeaders(SlideComponent component) {
        if (component.headers() == null || component.headers().isEmpty()) {
            throw new SlideLayoutException("Table component requires headers: " + component.id());
        }
        for (String header : component.headers()) {
            if (header == null || header.isBlank()) {
                throw new SlideLayoutException("Table component headers cannot be blank: " + component.id());
            }
        }
    }

    private static void requireRows(SlideComponent component) {
        if (component.rows() == null || component.rows().isEmpty()) {
            throw new SlideLayoutException("Component requires rows: " + component.id());
        }
        int expectedColumns = -1;
        for (List<String> row : component.rows()) {
            if (row == null || row.isEmpty()) {
                throw new SlideLayoutException("Component rows cannot be empty: " + component.id());
            }
            if (expectedColumns == -1) {
                expectedColumns = row.size();
            } else if (row.size() != expectedColumns) {
                throw new SlideLayoutException("Component rows must have consistent column counts: " + component.id());
            }
            for (String cell : row) {
                if (cell == null) {
                    throw new SlideLayoutException("Component row cells cannot be null: " + component.id());
                }
            }
        }
        if ("table".equals(component.type()) && component.headers() != null && component.headers().size() != expectedColumns) {
            throw new SlideLayoutException("Table headers must match row column count: " + component.id());
        }
    }
}
