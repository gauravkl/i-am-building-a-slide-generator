package com.example.slidegen;

import java.io.IOException;

@FunctionalInterface
public interface ImageGeneratorClient {
    byte[] generateImage(String prompt, ImageGenerationOptions options) throws IOException, InterruptedException;

    static ImageGeneratorClient disabled() {
        return (prompt, options) -> {
            throw new SlideLayoutException("Image generation is not configured for this command.");
        };
    }
}
