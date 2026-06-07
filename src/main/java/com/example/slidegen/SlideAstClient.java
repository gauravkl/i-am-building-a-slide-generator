package com.example.slidegen;

import com.example.slidegen.model.DeckInput;

import java.io.IOException;

@FunctionalInterface
public interface SlideAstClient {
    DeckInput generateDeck(String prompt) throws IOException, InterruptedException;
}
