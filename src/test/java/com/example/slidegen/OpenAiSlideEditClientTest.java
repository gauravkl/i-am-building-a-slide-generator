package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.DeckEditPatch;
import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSize;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiSlideEditClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsStrictPatchSchemaRequestAndParsesResponsesOutput() throws Exception {
        RecordingTransport transport = new RecordingTransport(responsesBody());

        OpenAiSlideEditClient client = new OpenAiSlideEditClient(transport, objectMapper, "test-model");
        DeckEditPatch patch = client.generatePatch(sampleDeck(), "swap the text and circle in slide 2");

        assertEquals("DeckEditPatch", patch.type());
        assertEquals(2, patch.operations().size());
        assertEquals("updateComponent", patch.operations().get(0).op());
        assertEquals("slide_2", patch.operations().get(0).slideId());
        assertEquals("label", patch.operations().get(0).componentId());
        assertEquals(new Bounds(520, 180, 160, 160), patch.operations().get(0).bounds());

        JsonNode request = objectMapper.readTree(transport.requestJson());
        assertEquals("test-model", request.path("model").asText());
        assertEquals("json_schema", request.path("text").path("format").path("type").asText());
        assertEquals("deck_edit_patch", request.path("text").path("format").path("name").asText());
        assertTrue(request.path("text").path("format").path("strict").asBoolean());
        JsonNode schema = request.path("text").path("format").path("schema");
        assertEquals("DeckEditPatch", schema.path("properties").path("type").path("const").asText());
        JsonNode operation = schema.path("properties").path("operations").path("items");
        assertEquals("updateComponent", operation.path("properties").path("op").path("const").asText());
        assertTrue(operation.path("required").toString().contains("bounds"));
        assertTrue(operation.path("required").toString().contains("style"));
        assertTrue(operation.path("required").toString().contains("rows"));
        assertTrue(operation.path("required").toString().contains("headers"));
        assertEquals("array", operation.path("properties").path("rows").path("anyOf").get(0).path("type").asText());
        assertEquals("array", operation.path("properties").path("items").path("anyOf").get(0).path("type").asText());
        assertEquals("bar", operation.path("properties").path("chartType").path("anyOf").get(0).path("const").asText());
        assertTrue(operation.path("properties").path("type").path("anyOf").get(0).path("enum").toString().contains("chart"));

        String userText = request.path("input").get(1).path("content").get(0).path("text").asText();
        assertTrue(userText.contains("swap the text and circle in slide 2"));
        assertTrue(userText.contains("\"id\":\"slide_2\""));
        assertTrue(userText.contains("\"id\":\"label\""));
        String systemText = request.path("input").get(0).path("content").get(0).path("text").asText();
        assertTrue(systemText.contains("When changing to matrix, provide rows."));
        assertTrue(systemText.contains("When changing to table, provide headers and rows."));
    }

    @Test
    void parsesPatchContainingMatrixContentFields() throws Exception {
        RecordingTransport transport = new RecordingTransport(contentResponsesBody());

        OpenAiSlideEditClient client = new OpenAiSlideEditClient(transport, objectMapper, "test-model");
        DeckEditPatch patch = client.generatePatch(sampleDeck(), "change bullets to a 2 by 2 matrix");

        assertEquals("DeckEditPatch", patch.type());
        assertEquals(1, patch.operations().size());
        assertEquals("matrix", patch.operations().get(0).type());
        assertEquals(List.of(List.of("Plan", "Build"), List.of("Launch", "Learn")), patch.operations().get(0).rows());
    }

    @Test
    void usesDefaultModelWhenModelIsBlank() throws Exception {
        RecordingTransport transport = new RecordingTransport(responsesBody());

        OpenAiSlideEditClient client = new OpenAiSlideEditClient(transport, objectMapper, "");
        client.generatePatch(sampleDeck(), "swap the text and circle in slide 2");

        JsonNode request = objectMapper.readTree(transport.requestJson());
        assertEquals(OpenAiSlideAstClient.DEFAULT_MODEL, request.path("model").asText());
    }

    @Test
    void missingApiKeyFailsBeforeNetworkCall() {
        SlideLayoutException ex = assertThrows(SlideLayoutException.class, () ->
                new OpenAiSlideEditClient(null, "test-model", objectMapper));

        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
    }

    private static DeckInput sampleDeck() {
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

    private static String responsesBody() {
        return """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"type\\":\\"DeckEditPatch\\",\\"operations\\":[{\\"op\\":\\"updateComponent\\",\\"slideId\\":\\"slide_2\\",\\"componentId\\":\\"label\\",\\"type\\":null,\\"bounds\\":{\\"x\\":520,\\"y\\":180,\\"w\\":160,\\"h\\":160},\\"style\\":null},{\\"op\\":\\"updateComponent\\",\\"slideId\\":\\"slide_2\\",\\"componentId\\":\\"shape\\",\\"type\\":null,\\"bounds\\":{\\"x\\":120,\\"y\\":180,\\"w\\":260,\\"h\\":80},\\"style\\":null}]}"
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private static String contentResponsesBody() {
        return """
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"type\\":\\"DeckEditPatch\\",\\"operations\\":[{\\"op\\":\\"updateComponent\\",\\"slideId\\":\\"slide_1\\",\\"componentId\\":\\"bullets\\",\\"type\\":\\"matrix\\",\\"text\\":null,\\"items\\":null,\\"headers\\":null,\\"rows\\":[[\\"Plan\\",\\"Build\\"],[\\"Launch\\",\\"Learn\\"]],\\"imagePrompt\\":null,\\"src\\":null,\\"alt\\":null,\\"chartType\\":null,\\"labels\\":null,\\"values\\":null,\\"bounds\\":null,\\"style\\":null}]}"
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private static final class RecordingTransport implements OpenAiSlideEditClient.OpenAiEditTransport {
        private final String responseBody;
        private String requestJson;

        private RecordingTransport(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public String postJson(String requestJson) throws IOException, InterruptedException {
            this.requestJson = requestJson;
            return responseBody;
        }

        private String requestJson() {
            return requestJson;
        }
    }
}
