package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.RenderObject;
import com.example.slidegen.model.RenderedDeck;
import com.example.slidegen.model.RenderedSlidePage;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.Style;

import java.util.List;

public final class SlideHtmlRenderer {
    public String render(RenderedDeck deck) {
        validate(deck);

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"utf-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        html.append("  <title>Rendered Deck</title>\n");
        html.append("  <style>\n");
        html.append("    * { box-sizing: border-box; }\n");
        html.append("    body { margin: 0; min-height: 100vh; background: #f3f4f6; font-family: Arial, Helvetica, sans-serif; }\n");
        html.append("    .deck { min-height: 100vh; padding: 32px; display: flex; flex-direction: column; align-items: center; gap: 32px; }\n");
        html.append("    .slide { position: relative; background: #ffffff; box-shadow: 0 18px 50px rgba(15, 23, 42, 0.18); overflow: hidden; }\n");
        html.append("    .object { position: absolute; }\n");
        html.append("    .text, .bullets { color: #111827; line-height: 1.2; overflow: hidden; white-space: normal; }\n");
        html.append("    .bullets ul { margin: 0; padding-left: 1.2em; }\n");
        html.append("    .bullets li { margin: 0 0 0.45em; }\n");
        html.append("    .rect { background: #e5e7eb; border: 2px solid #111827; }\n");
        html.append("    .circle { background: #111827; border: 0 solid #111827; border-radius: 50%; }\n");
        html.append("    .arrow { --stroke-color: #111827; --stroke-width: 3px; }\n");
        html.append("    .arrow-line { position: absolute; left: 0; right: 10px; top: 50%; border-top: var(--stroke-width) solid var(--stroke-color); }\n");
        html.append("    .arrow-head { position: absolute; right: -10px; top: calc(var(--stroke-width) * -2); width: 10px; height: 10px; border-top: var(--stroke-width) solid var(--stroke-color); border-right: var(--stroke-width) solid var(--stroke-color); transform: rotate(45deg); }\n");
        html.append("    .matrix, .table { --stroke-color: #111827; --stroke-width: 2px; }\n");
        html.append("    .matrix table, .table table { width: 100%; height: 100%; border-collapse: collapse; table-layout: fixed; border: var(--stroke-width) solid var(--stroke-color); }\n");
        html.append("    .matrix td, .table td, .table th { border: var(--stroke-width) solid var(--stroke-color); padding: 10px; vertical-align: middle; text-align: center; color: #111827; }\n");
        html.append("    .table th { background: #f3f4f6; font-weight: 700; }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <main class=\"deck\">\n");

        for (RenderedSlidePage slide : deck.slides()) {
            appendSlide(html, deck.size(), slide);
        }

        html.append("  </main>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private static void appendSlide(StringBuilder html, SlideSize size, RenderedSlidePage slide) {
        html.append("    <section class=\"slide\" data-slide-id=\"")
                .append(escapeAttribute(slide.id()))
                .append("\" style=\"width: ")
                .append(size.width())
                .append("px; height: ")
                .append(size.height())
                .append("px;\">\n");

        for (RenderObject object : slide.objects()) {
            appendObject(html, object);
        }

        html.append("    </section>\n");
    }

    private static void appendObject(StringBuilder html, RenderObject object) {
        Bounds bounds = object.bounds();
        html.append("      <div class=\"object ")
                .append(escapeAttribute(object.type()))
                .append("\" data-object-id=\"")
                .append(escapeAttribute(object.id()))
                .append("\" style=\"")
                .append(boundsStyle(bounds));

        Style style = object.style();
        if (style != null) {
            if (style.fontSize() != null) {
                html.append(" font-size: ").append(style.fontSize()).append("px;");
            }
            if (style.fontWeight() != null) {
                html.append(" font-weight: ").append(style.fontWeight()).append(";");
            }
            if (style.color() != null) {
                html.append(" color: ").append(escapeAttribute(style.color())).append(";");
            }
            if (style.fillColor() != null && supportsFillColor(object.type())) {
                html.append(" background: ").append(escapeAttribute(style.fillColor())).append(";");
            }
            if (style.strokeColor() != null) {
                html.append(" --stroke-color: ").append(escapeAttribute(style.strokeColor())).append(";");
                html.append(" border-color: ").append(escapeAttribute(style.strokeColor())).append(";");
            }
            if (style.strokeWidth() != null) {
                html.append(" --stroke-width: ").append(style.strokeWidth()).append("px;");
                html.append(" border-width: ").append(style.strokeWidth()).append("px;");
            }
        }

        html.append("\">");
        switch (object.type()) {
            case "text" -> html.append(escapeText(object.text() == null ? "" : object.text()));
            case "bullets" -> appendBullets(html, object.items());
            case "arrow" -> html.append("<div class=\"arrow-line\"><span class=\"arrow-head\"></span></div>");
            case "matrix" -> appendMatrix(html, object.rows());
            case "table" -> appendTable(html, object.headers(), object.rows());
            case "rect", "circle" -> {
                // Shape-only components have no child markup.
            }
            default -> {
                // Validation rejects unsupported object types before rendering.
            }
        }
        html.append("</div>\n");
    }

    private static void appendBullets(StringBuilder html, List<String> items) {
        html.append("<ul>");
        for (String item : items) {
            html.append("<li>").append(escapeText(item)).append("</li>");
        }
        html.append("</ul>");
    }

    private static void appendMatrix(StringBuilder html, List<List<String>> rows) {
        html.append("<table><tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            for (String cell : row) {
                html.append("<td>").append(escapeText(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    private static void appendTable(StringBuilder html, List<String> headers, List<List<String>> rows) {
        html.append("<table><thead><tr>");
        for (String header : headers) {
            html.append("<th>").append(escapeText(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<String> row : rows) {
            html.append("<tr>");
            for (String cell : row) {
                html.append("<td>").append(escapeText(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
    }

    private static String boundsStyle(Bounds bounds) {
        return "left: " + bounds.x() + "px;"
                + " top: " + bounds.y() + "px;"
                + " width: " + bounds.w() + "px;"
                + " height: " + bounds.h() + "px;";
    }

    private static boolean supportsFillColor(String objectType) {
        return List.of("rect", "circle", "matrix", "table").contains(objectType);
    }

    private static void validate(RenderedDeck deck) {
        if (deck == null) {
            throw new SlideLayoutException("Input JSON must describe a rendered deck.");
        }
        if (!"RenderedDeck".equals(deck.type())) {
            throw new SlideLayoutException("Expected type to be 'RenderedDeck'.");
        }

        SlideSize size = deck.size();
        if (size == null) {
            throw new SlideLayoutException("Rendered deck size is required.");
        }
        if (size.width() <= 0 || size.height() <= 0) {
            throw new SlideLayoutException("Rendered deck size width and height must be positive.");
        }

        List<RenderedSlidePage> slides = deck.slides();
        if (slides == null || slides.isEmpty()) {
            throw new SlideLayoutException("Rendered deck must contain at least one slide.");
        }

        for (RenderedSlidePage slide : slides) {
            validateSlide(slide);
        }
    }

    private static void validateSlide(RenderedSlidePage slide) {
        if (slide == null) {
            throw new SlideLayoutException("Rendered deck slides cannot contain null entries.");
        }
        if (slide.id() == null || slide.id().isBlank()) {
            throw new SlideLayoutException("Rendered slide id is required.");
        }
        if (!"RenderedSlide".equals(slide.type())) {
            throw new SlideLayoutException("Expected rendered slide type to be 'RenderedSlide'.");
        }
        if (slide.objects() == null) {
            throw new SlideLayoutException("Rendered slide objects are required: " + slide.id());
        }

        for (RenderObject object : slide.objects()) {
            validateObject(object);
        }
    }

    private static void validateObject(RenderObject object) {
        if (object == null) {
            throw new SlideLayoutException("Rendered slide objects cannot contain null entries.");
        }
        if (object.id() == null || object.id().isBlank()) {
            throw new SlideLayoutException("Rendered slide object id is required.");
        }
        if (!List.of("text", "bullets", "rect", "circle", "arrow", "matrix", "table").contains(object.type())) {
            throw new SlideLayoutException("Unsupported render object type: " + object.type());
        }
        if (object.bounds() == null) {
            throw new SlideLayoutException("Rendered slide object bounds are required: " + object.id());
        }
        switch (object.type()) {
            case "text" -> {
                if (object.text() == null || object.text().isBlank()) {
                    throw new SlideLayoutException("Rendered text object requires text: " + object.id());
                }
            }
            case "bullets" -> {
                if (object.items() == null || object.items().isEmpty()) {
                    throw new SlideLayoutException("Rendered bullets object requires items: " + object.id());
                }
            }
            case "matrix" -> {
                if (object.rows() == null || object.rows().isEmpty()) {
                    throw new SlideLayoutException("Rendered matrix object requires rows: " + object.id());
                }
            }
            case "table" -> {
                if (object.headers() == null || object.headers().isEmpty() || object.rows() == null || object.rows().isEmpty()) {
                    throw new SlideLayoutException("Rendered table object requires headers and rows: " + object.id());
                }
            }
            case "rect", "circle", "arrow" -> {
                // Shape-only objects only require id/type/bounds.
            }
            default -> throw new SlideLayoutException("Unsupported render object type: " + object.type());
        }
    }

    private static String escapeText(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeAttribute(String value) {
        return escapeText(value == null ? "" : value)
                .replace("\"", "&quot;");
    }
}
