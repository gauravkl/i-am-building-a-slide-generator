package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiSlideAstClient implements SlideAstClient {
    public static final String DEFAULT_MODEL = "gpt-5.5";
    private static final URI DEFAULT_RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final OpenAiTransport transport;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiSlideAstClient(String apiKey, String model, ObjectMapper objectMapper) {
        this(new HttpClientOpenAiTransport(HttpClient.newHttpClient(), DEFAULT_RESPONSES_URI, apiKey), objectMapper, model);
    }

    OpenAiSlideAstClient(
            OpenAiTransport transport,
            ObjectMapper objectMapper,
            String model
    ) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    @Override
    public DeckInput generateDeck(String prompt) throws IOException, InterruptedException {
        if (prompt == null || prompt.isBlank()) {
            throw new SlideLayoutException("Prompt text is required.");
        }

        String requestJson = objectMapper.writeValueAsString(requestBody(prompt));
        return parseDeckInput(transport.postJson(requestJson));
    }

    private DeckInput parseDeckInput(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = root.path("output_text").asText(null);
        if (outputText != null && !outputText.isBlank()) {
            return objectMapper.readValue(outputText, DeckInput.class);
        }

        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode contentItem : content) {
                    String text = contentItem.path("text").asText(null);
                    if (text != null && !text.isBlank()) {
                        return objectMapper.readValue(text, DeckInput.class);
                    }
                }
            }
        }

        throw new SlideLayoutException("OpenAI response did not contain generated DeckInput JSON.");
    }

    private Map<String, Object> requestBody(String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(
                message("system", """
                        You convert user presentation requests into DeckInput JSON.
                        Return a Deck root using the provided JSON schema.
                        For one-slide requests, return a Deck with one slide.
                        If the user asks for N slides, return exactly N slides.
                        Slides contain components, not predefined layouts.
                        Use only these component types: text, bullets, rect, circle, arrow, matrix, table, image, chart.
                        Every component must have explicit pixel bounds inside the 1280 by 720 slide.
                        Type-specific content is required:
                        - text components must have non-empty text.
                        - bullets components must have non-empty items.
                        - matrix components must have non-empty rows.
                        - table components must have non-empty headers and rows.
                        - image components must have non-empty imagePrompt and alt, and src must be null because Java generates the image asset.
                        - chart components must have chartType "bar", non-empty labels, and non-empty numeric values.
                        - rect, circle, and arrow components are shapes and should use null for text, items, headers, rows, imagePrompt, src, alt, chartType, labels, and values.
                        Style rules:
                        - Use color for text and bullets.
                        - Default text and bullet color should be #111827.
                        - Do not use white text for shape labels unless the label bounds are fully inside a dark filled shape.
                        - Do not set fillColor on text or bullets.
                        - If text needs a colored background or highlight, create a rect behind it and put a text component on top.
                        - Use fillColor for rect, circle, matrix, and table backgrounds, and for chart bars.
                        - Use strokeColor and strokeWidth for shape outlines, arrows, matrix grid lines, table grid lines, and chart axes.
                        Image rules:
                        - If the user asks for an image, photo, picture, illustration, screenshot-like visual, or generated visual, use one image component.
                        - imagePrompt should describe the desired visual clearly, including style and subject, but should not request important readable text inside the image.
                        - Put titles, labels, captions, and important words in separate text components, not inside generated images.
                        Chart rules:
                        - If the user asks for a chart, bar chart, graph, metric comparison, trend-like comparison, or values visualization, use one chart component.
                        - For v1, chart components must use chartType "bar".
                        - Chart labels length must equal values length.
                        - Chart values must be finite non-negative numbers.
                        - Put chart titles and captions in separate text components, not inside the chart component.
                        If you add a label for a shape, make it a separate text component with visible label words.
                        If no label words are needed, do not create a text label component.
                        Do not manually emulate a core component by composing lower-level shapes and text.
                        If the user asks for a matrix, create one matrix component with rows; do not draw it with rect and text components.
                        If the user requests exact matrix dimensions such as "2x2", "2 x 2", or "2 by 2":
                        - The matrix rows array must contain exactly that many rows.
                        - Each row must contain exactly that many cells.
                        - Do not add header rows, label columns, explanation rows, or extra cells inside the matrix.
                        - Do not split one complete phrase across adjacent cells to create extra columns.
                        - Put complete, concise ideas inside each cell; use a separate text component for captions or notes.
                        If the user asks for a table, create one table component with headers and rows.
                        For every table component:
                        - headers length must equal the number of cells in each row.
                        - If there are 3 headers, every row must have exactly 3 cells.
                        - Do not put extra notes, row labels, or split phrases into additional cells unless you also add matching headers.
                        If the user asks for bullets, create a bullets component with items.
                        If the user asks for a chart or graph, create one chart component with chartType, labels, and values; do not draw it with rect and text components.
                        If the user asks for an arrow, circle, or rectangle, use the matching arrow, circle, or rect component type.
                        Convert spatial language into concrete bounds:
                        - "top left" means small x and y, such as x 60-100 and y 40-80.
                        - "top right" means a component near the right edge, with x + w under 1280.
                        - "left", "right", "center", "bottom", "above", "below", and "beside" should become non-overlapping bounds.
                        - If the user gives exact coordinates or dimensions, preserve them when inside the slide.
                        - For arrows, use bounds that connect the referenced regions and stay inside the slide.
                        If the user asks for an overview slide plus elaboration slides:
                        - Slide 1 should summarize the main idea with text and/or bullets.
                        - Following slides should elaborate the corresponding points from slide 1.
                        - Preserve ordering: slide 2 elaborates point 1, slide 3 elaborates point 2, and so on.
                        Use deck size 1280 by 720.
                        Do not include markdown or commentary.
                        """),
                message("user", prompt)
        ));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "deck_input",
                        "strict", true,
                        "schema", deckInputSchema()
                )
        ));
        return body;
    }

    private static Map<String, Object> message(String role, String content) {
        return Map.of(
                "role", role,
                "content", List.of(Map.of(
                        "type", "input_text",
                        "text", content
                ))
        );
    }

    private static Map<String, Object> deckInputSchema() {
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "object");
        size.put("additionalProperties", false);
        size.put("required", List.of("width", "height"));
        size.put("properties", Map.of(
                "width", Map.of("type", "integer", "const", 1280),
                "height", Map.of("type", "integer", "const", 720)
        ));

        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put("type", "object");
        bounds.put("additionalProperties", false);
        bounds.put("required", List.of("x", "y", "w", "h"));
        bounds.put("properties", Map.of(
                "x", Map.of("type", "integer", "minimum", 0),
                "y", Map.of("type", "integer", "minimum", 0),
                "w", Map.of("type", "integer", "minimum", 1),
                "h", Map.of("type", "integer", "minimum", 1)
        ));

        Map<String, Object> style = new LinkedHashMap<>();
        style.put("type", "object");
        style.put("additionalProperties", false);
        style.put("required", List.of("fontSize", "fontWeight", "color", "fillColor", "strokeColor", "strokeWidth"));
        style.put("properties", Map.of(
                "fontSize", nullable(Map.of("type", "integer", "minimum", 1)),
                "fontWeight", nullable(Map.of("type", "integer", "minimum", 1)),
                "color", nullable(Map.of("type", "string")),
                "fillColor", nullable(Map.of("type", "string")),
                "strokeColor", nullable(Map.of("type", "string")),
                "strokeWidth", nullable(Map.of("type", "integer", "minimum", 1))
        ));

        Map<String, Object> stringValue = Map.of("type", "string");

        Map<String, Object> stringArray = Map.of(
                "type", "array",
                "items", stringValue
        );

        Map<String, Object> stringRows = Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string")
                )
        );

        Map<String, Object> numberArray = Map.of(
                "type", "array",
                "items", Map.of("type", "number", "minimum", 0)
        );

        Map<String, Object> component = Map.of(
                "anyOf", List.of(
                        componentVariant("text", stringValue, nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("bullets", nullSchema(), stringArray, nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("rect", nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("circle", nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("arrow", nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("matrix", nullSchema(), nullSchema(), nullSchema(), stringRows, nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("table", nullSchema(), nullSchema(), stringArray, stringRows, nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("image", nullSchema(), nullSchema(), nullSchema(), nullSchema(), stringValue, nullSchema(), stringValue, nullSchema(), nullSchema(), nullSchema(), bounds, style),
                        componentVariant("chart", nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), nullSchema(), Map.of("type", "string", "const", "bar"), stringArray, numberArray, bounds, style)
                )
        );

        Map<String, Object> slide = new LinkedHashMap<>();
        slide.put("type", "object");
        slide.put("additionalProperties", false);
        slide.put("required", List.of("id", "type", "components"));
        slide.put("properties", Map.of(
                "id", Map.of("type", "string"),
                "type", Map.of("type", "string", "const", "Slide"),
                "components", Map.of(
                        "type", "array",
                        "minItems", 1,
                        "items", component
                )
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("type", "size", "slides"));
        schema.put("properties", Map.of(
                "type", Map.of("type", "string", "const", "Deck"),
                "size", size,
                "slides", Map.of(
                        "type", "array",
                        "minItems", 1,
                        "items", slide
                )
        ));
        return schema;
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
    }

    private static Map<String, Object> nullSchema() {
        return Map.of("type", "null");
    }

    private static Map<String, Object> componentVariant(
            String type,
            Map<String, Object> text,
            Map<String, Object> items,
            Map<String, Object> headers,
            Map<String, Object> rows,
            Map<String, Object> imagePrompt,
            Map<String, Object> src,
            Map<String, Object> alt,
            Map<String, Object> chartType,
            Map<String, Object> labels,
            Map<String, Object> values,
            Map<String, Object> bounds,
            Map<String, Object> style
    ) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("type", "object");
        component.put("additionalProperties", false);
        component.put("required", List.of("id", "type", "text", "items", "headers", "rows", "imagePrompt", "src", "alt", "chartType", "labels", "values", "bounds", "style"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", Map.of("type", "string"));
        properties.put("type", Map.of("type", "string", "const", type));
        properties.put("text", text);
        properties.put("items", items);
        properties.put("headers", headers);
        properties.put("rows", rows);
        properties.put("imagePrompt", imagePrompt);
        properties.put("src", src);
        properties.put("alt", alt);
        properties.put("chartType", chartType);
        properties.put("labels", labels);
        properties.put("values", values);
        properties.put("bounds", bounds);
        properties.put("style", nullable(style));
        component.put("properties", properties);
        return component;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new SlideLayoutException(message);
        }
        return value;
    }

    private static String safeSummary(String body) {
        if (body == null || body.isBlank()) {
            return "(empty response)";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() <= 500 ? compact : compact.substring(0, 500) + "...";
    }

    @FunctionalInterface
    interface OpenAiTransport {
        String postJson(String requestJson) throws IOException, InterruptedException;
    }

    private record HttpClientOpenAiTransport(HttpClient httpClient, URI responsesUri, String apiKey) implements OpenAiTransport {
        private HttpClientOpenAiTransport {
            requireNonBlank(apiKey, "OPENAI_API_KEY is required for --prompt and --prompt-to-html.");
        }

        @Override
        public String postJson(String requestJson) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(responsesUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SlideLayoutException("OpenAI request failed with status "
                        + response.statusCode()
                        + ": "
                        + safeSummary(response.body()));
            }

            return response.body();
        }
    }
}
