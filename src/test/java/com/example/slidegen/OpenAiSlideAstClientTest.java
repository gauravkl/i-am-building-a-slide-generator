package com.example.slidegen;

import com.example.slidegen.model.DeckInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiSlideAstClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendsStrictDeckSchemaRequestAndParsesResponsesOutput() throws Exception {
        RecordingTransport transport = new RecordingTransport(responsesBody());

        OpenAiSlideAstClient client = new OpenAiSlideAstClient(transport, objectMapper, "test-model");
        DeckInput deckInput = client.generateDeck("create a three slide deck");

        assertEquals("Deck", deckInput.type());
        assertEquals(3, deckInput.slides().size());
        assertEquals(2, deckInput.slides().get(0).components().size());
        assertEquals("text", deckInput.slides().get(0).components().get(0).type());
        assertEquals("Why AI slide iteration breaks", deckInput.slides().get(0).components().get(0).text());

        JsonNode request = objectMapper.readTree(transport.requestJson());
        assertEquals("test-model", request.path("model").asText());
        assertEquals("json_schema", request.path("text").path("format").path("type").asText());
        assertEquals("deck_input", request.path("text").path("format").path("name").asText());
        assertTrue(request.path("text").path("format").path("strict").asBoolean());
        assertEquals("Deck", request.path("text").path("format").path("schema").path("properties").path("type").path("const").asText());
        assertEquals(1280, request.path("text").path("format").path("schema").path("properties").path("size").path("properties").path("width").path("const").asInt());
        assertEquals("Slide", request.path("text").path("format").path("schema").path("properties").path("slides").path("items").path("properties").path("type").path("const").asText());
        JsonNode componentSchema = request.path("text").path("format").path("schema").path("properties").path("slides").path("items").path("properties").path("components").path("items");
        assertTrue(componentSchema.path("anyOf").isArray());
        assertEquals(7, componentSchema.path("anyOf").size());

        JsonNode textComponent = componentSchema.path("anyOf").get(0);
        assertEquals("text", textComponent.path("properties").path("type").path("const").asText());
        assertEquals("string", textComponent.path("properties").path("text").path("type").asText());
        assertEquals("null", textComponent.path("properties").path("items").path("type").asText());

        JsonNode arrowComponent = componentSchema.path("anyOf").get(4);
        assertEquals("arrow", arrowComponent.path("properties").path("type").path("const").asText());
        assertEquals("null", arrowComponent.path("properties").path("text").path("type").asText());
        assertTrue(arrowComponent.path("required").toString().contains("bounds"));

        JsonNode tableComponent = componentSchema.path("anyOf").get(6);
        assertEquals("table", tableComponent.path("properties").path("type").path("const").asText());
        assertEquals("array", tableComponent.path("properties").path("headers").path("type").asText());
        assertEquals("array", tableComponent.path("properties").path("rows").path("type").asText());
    }

    @Test
    void usesDefaultModelWhenModelIsBlank() throws Exception {
        RecordingTransport transport = new RecordingTransport(responsesBody());

        OpenAiSlideAstClient client = new OpenAiSlideAstClient(transport, objectMapper, "");
        client.generateDeck("create a slide");

        JsonNode request = objectMapper.readTree(transport.requestJson());
        assertEquals(OpenAiSlideAstClient.DEFAULT_MODEL, request.path("model").asText());
    }

    @Test
    void transportErrorsFailClearly() {
        RecordingTransport transport = new RecordingTransport(new SlideLayoutException(
                "OpenAI request failed with status 500: {\"error\":{\"message\":\"upstream failed\"}}"
        ));
        OpenAiSlideAstClient client = new OpenAiSlideAstClient(transport, objectMapper, "test-model");

        SlideLayoutException ex = assertThrows(SlideLayoutException.class, () -> client.generateDeck("create a slide"));

        assertTrue(ex.getMessage().contains("OpenAI request failed with status 500"));
        assertTrue(ex.getMessage().contains("upstream failed"));
    }

    @Test
    void missingApiKeyFailsBeforeNetworkCall() {
        SlideLayoutException ex = assertThrows(SlideLayoutException.class, () ->
                new OpenAiSlideAstClient(null, "test-model", objectMapper));

        assertTrue(ex.getMessage().contains("OPENAI_API_KEY"));
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
                          "text": "{\\"type\\":\\"Deck\\",\\"size\\":{\\"width\\":1280,\\"height\\":720},\\"slides\\":[{\\"id\\":\\"slide_1\\",\\"type\\":\\"Slide\\",\\"components\\":[{\\"id\\":\\"title\\",\\"type\\":\\"text\\",\\"text\\":\\"Why AI slide iteration breaks\\",\\"bounds\\":{\\"x\\":80,\\"y\\":48,\\"w\\":720,\\"h\\":80},\\"style\\":{\\"fontSize\\":44,\\"fontWeight\\":700}},{\\"id\\":\\"bullets\\",\\"type\\":\\"bullets\\",\\"items\\":[\\"AI tools often regenerate the whole slide.\\",\\"Deterministic geometry is needed for precise edits.\\"],\\"bounds\\":{\\"x\\":80,\\"y\\":160,\\"w\\":520,\\"h\\":260}}]},{\\"id\\":\\"slide_2\\",\\"type\\":\\"Slide\\",\\"components\\":[{\\"id\\":\\"title\\",\\"type\\":\\"text\\",\\"text\\":\\"Whole-slide regeneration causes drift\\",\\"bounds\\":{\\"x\\":80,\\"y\\":48,\\"w\\":860,\\"h\\":80}}]},{\\"id\\":\\"slide_3\\",\\"type\\":\\"Slide\\",\\"components\\":[{\\"id\\":\\"title\\",\\"type\\":\\"text\\",\\"text\\":\\"Deterministic geometry enables precise edits\\",\\"bounds\\":{\\"x\\":80,\\"y\\":48,\\"w\\":900,\\"h\\":80}}]}]}"
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private static final class RecordingTransport implements OpenAiSlideAstClient.OpenAiTransport {
        private final String responseBody;
        private final RuntimeException runtimeException;
        private String requestJson;

        private RecordingTransport(String responseBody) {
            this.responseBody = responseBody;
            this.runtimeException = null;
        }

        private RecordingTransport(RuntimeException runtimeException) {
            this.responseBody = null;
            this.runtimeException = runtimeException;
        }

        @Override
        public String postJson(String requestJson) throws IOException, InterruptedException {
            this.requestJson = requestJson;
            if (runtimeException != null) {
                throw runtimeException;
            }
            return responseBody;
        }

        private String requestJson() {
            return requestJson;
        }
    }
}
