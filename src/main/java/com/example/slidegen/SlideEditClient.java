package com.example.slidegen;

import com.example.slidegen.model.DeckEditPatch;
import com.example.slidegen.model.DeckInput;

import java.io.IOException;

@FunctionalInterface
public interface SlideEditClient {
    DeckEditPatch generatePatch(DeckInput deckInput, String instruction) throws IOException, InterruptedException;

    static SlideEditClient disabled() {
        return (deckInput, instruction) -> {
            throw new SlideLayoutException("Slide edit generation is not configured for this command.");
        };
    }
}
