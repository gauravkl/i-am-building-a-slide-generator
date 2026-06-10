package com.example.slidegen;

import com.example.slidegen.model.DeckEditPatch;
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

public final class OpenAiSlideEditClient implements SlideEditClient {
    private static final URI DEFAULT_RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final OpenAiEditTransport transport;
    private final ObjectMapper objectMapper;
    private final String model;

    public OpenAiSlideEditClient(String apiKey, String model, ObjectMapper objectMapper) {
        this(new HttpClientOpenAiEditTransport(HttpClient.newHttpClient(), DEFAULT_RESPONSES_URI, apiKey), objectMapper, model);
    }

    OpenAiSlideEditClient(
            OpenAiEditTransport transport,
            ObjectMapper objectMapper,
            String model
    ) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.model = model == null || model.isBlank() ? OpenAiSlideAstClient.DEFAULT_MODEL : model;
    }

    @Override
    public DeckEditPatch generatePatch(DeckInput deckInput, String instruction) throws IOException, InterruptedException {
        if (deckInput == null) {
            throw new SlideLayoutException("Existing deck is required for slide edits.");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new SlideLayoutException("Edit instruction is required.");
        }

        String requestJson = objectMapper.writeValueAsString(requestBody(deckInput, instruction));
        return parsePatch(transport.postJson(requestJson));
    }

    private DeckEditPatch parsePatch(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = root.path("output_text").asText(null);
        if (outputText != null && !outputText.isBlank()) {
            return objectMapper.readValue(outputText, DeckEditPatch.class);
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
                        return objectMapper.readValue(text, DeckEditPatch.class);
                    }
                }
            }
        }

        throw new SlideLayoutException("OpenAI response did not contain generated DeckEditPatch JSON.");
    }

    private Map<String, Object> requestBody(DeckInput deckInput, String instruction) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", List.of(
                message("system", """
                        You edit an existing component-based DeckInput by returning DeckEditPatch JSON.
                        Return only the patch root using the provided JSON schema.
                        Patch operations may only update existing components; do not add or delete slides or components.
                        Each operation must use op "updateComponent" and target an existing slideId and componentId.
                        For unchanged optional fields, return null.
                        You may update only these component fields: type, bounds, style, text, items, headers, rows, imagePrompt, src, alt, chartType, labels, values.
                        Preserve component ids and unrelated components.
                        For same-type edits, return null for unchanged content fields.
                        For type-changing edits, provide the content fields required by the target component type and return null for incompatible fields.
                        When changing to text, provide text.
                        When changing to bullets, provide items.
                        When changing to matrix, provide rows.
                        When changing to table, provide headers and rows.
                        When changing to chart, provide chartType "bar", labels, and values.
                        Do not convert a component to image unless an existing local src and alt are available; edit mode does not generate new image assets.
                        For layout edits, choose readable, non-overlapping pixel bounds inside the 1280 by 720 slide.
                        For swap edits, update the involved components' bounds to better positions for the user's intent; a literal full bounds swap is allowed only if it looks good.
                        If a component remains a circle, keep its bounds square unless the user clearly requests otherwise.
                        For shape/color edits, update type and/or style.fillColor as needed; preserve existing stroke styling unless the user asks to change it.
                        If a shape has a separate text label and the shape moves, resizes, or changes type, also update the label bounds so it remains readable.
                        Default text label color should be #111827 unless the label is fully inside a dark filled shape.
                        Do not include markdown or commentary.
                        """),
                message("user", """
                        Edit instruction:
                        """
                        + instruction
                        + """

                        Existing DeckInput JSON:
                        """
                        + objectMapper.writeValueAsString(deckInput))
        ));
        body.put("text", Map.of(
                "format", Map.of(
                        "type", "json_schema",
                        "name", "deck_edit_patch",
                        "strict", true,
                        "schema", deckEditPatchSchema()
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

    private static Map<String, Object> deckEditPatchSchema() {
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
                        "items", stringValue
                )
        );
        Map<String, Object> numberArray = Map.of(
                "type", "array",
                "items", Map.of("type", "number", "minimum", 0)
        );

        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("type", "object");
        operation.put("additionalProperties", false);
        operation.put("required", List.of(
                "op",
                "slideId",
                "componentId",
                "type",
                "text",
                "items",
                "headers",
                "rows",
                "imagePrompt",
                "src",
                "alt",
                "chartType",
                "labels",
                "values",
                "bounds",
                "style"
        ));
        Map<String, Object> operationProperties = new LinkedHashMap<>();
        operationProperties.put("op", Map.of("type", "string", "const", "updateComponent"));
        operationProperties.put("slideId", Map.of("type", "string"));
        operationProperties.put("componentId", Map.of("type", "string"));
        operationProperties.put("type", nullable(Map.of(
                "type", "string",
                "enum", List.of("text", "bullets", "rect", "circle", "arrow", "matrix", "table", "image", "chart")
        )));
        operationProperties.put("text", nullable(stringValue));
        operationProperties.put("items", nullable(stringArray));
        operationProperties.put("headers", nullable(stringArray));
        operationProperties.put("rows", nullable(stringRows));
        operationProperties.put("imagePrompt", nullable(stringValue));
        operationProperties.put("src", nullable(stringValue));
        operationProperties.put("alt", nullable(stringValue));
        operationProperties.put("chartType", nullable(Map.of("type", "string", "const", "bar")));
        operationProperties.put("labels", nullable(stringArray));
        operationProperties.put("values", nullable(numberArray));
        operationProperties.put("bounds", nullable(bounds));
        operationProperties.put("style", nullable(style));
        operation.put("properties", operationProperties);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("type", "operations"));
        schema.put("properties", Map.of(
                "type", Map.of("type", "string", "const", "DeckEditPatch"),
                "operations", Map.of(
                        "type", "array",
                        "minItems", 1,
                        "items", operation
                )
        ));
        return schema;
    }

    private static Map<String, Object> nullable(Map<String, Object> schema) {
        return Map.of("anyOf", List.of(schema, Map.of("type", "null")));
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
    interface OpenAiEditTransport {
        String postJson(String requestJson) throws IOException, InterruptedException;
    }

    private record HttpClientOpenAiEditTransport(
            HttpClient httpClient,
            URI responsesUri,
            String apiKey
    ) implements OpenAiEditTransport {
        private HttpClientOpenAiEditTransport {
            requireNonBlank(apiKey, "OPENAI_API_KEY is required for --edit-to-html.");
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
                throw new SlideLayoutException("OpenAI edit request failed with status "
                        + response.statusCode()
                        + ": "
                        + safeSummary(response.body()));
            }

            return response.body();
        }
    }
}
