package com.example.slidegen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenAiImageGeneratorClient implements ImageGeneratorClient {
    public static final String DEFAULT_MODEL = "gpt-image-2";
    public static final String DEFAULT_QUALITY = "low";
    private static final URI DEFAULT_IMAGES_URI = URI.create("https://api.openai.com/v1/images/generations");
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private final OpenAiImageTransport transport;
    private final ObjectMapper objectMapper;

    public OpenAiImageGeneratorClient(String apiKey, ObjectMapper objectMapper) {
        this(new HttpClientOpenAiImageTransport(HttpClient.newHttpClient(), DEFAULT_IMAGES_URI, apiKey), objectMapper);
    }

    OpenAiImageGeneratorClient(OpenAiImageTransport transport, ObjectMapper objectMapper) {
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] generateImage(String prompt, ImageGenerationOptions options) throws IOException, InterruptedException {
        if (prompt == null || prompt.isBlank()) {
            throw new SlideLayoutException("Image prompt is required.");
        }

        String requestJson = objectMapper.writeValueAsString(requestBody(prompt, options));
        return parseImageBytes(transport.postJson(requestJson));
    }

    private static Map<String, Object> requestBody(String prompt, ImageGenerationOptions options) {
        ImageGenerationOptions safeOptions = safeOptions(options);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", safeOptions.model());
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("quality", safeOptions.quality());
        body.put("size", safeOptions.size());
        body.put("output_format", "png");
        return body;
    }

    private byte[] parseImageBytes(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            String base64 = data.get(0).path("b64_json").asText(null);
            if (base64 != null && !base64.isBlank()) {
                return Base64.getDecoder().decode(base64);
            }
        }
        throw new SlideLayoutException("OpenAI image response did not contain base64 image data.");
    }

    private static ImageGenerationOptions safeOptions(ImageGenerationOptions options) {
        String model = options == null || options.model() == null || options.model().isBlank()
                ? DEFAULT_MODEL
                : options.model();
        String quality = options == null || options.quality() == null || options.quality().isBlank()
                ? DEFAULT_QUALITY
                : options.quality();
        String size = options == null || options.size() == null || options.size().isBlank()
                ? "1024x1024"
                : options.size();
        return new ImageGenerationOptions(model, quality, size);
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
    interface OpenAiImageTransport {
        String postJson(String requestJson) throws IOException, InterruptedException;
    }

    private record HttpClientOpenAiImageTransport(
            HttpClient httpClient,
            URI imagesUri,
            String apiKey
    ) implements OpenAiImageTransport {
        private HttpClientOpenAiImageTransport {
            requireNonBlank(apiKey, "OPENAI_API_KEY is required for image generation.");
        }

        @Override
        public String postJson(String requestJson) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(imagesUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SlideLayoutException("OpenAI image request failed with status "
                        + response.statusCode()
                        + ": "
                        + safeSummary(response.body()));
            }

            return response.body();
        }
    }
}
