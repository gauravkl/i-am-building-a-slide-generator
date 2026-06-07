package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"type", "size", "slides"})
public record RenderedDeck(String type, SlideSize size, List<RenderedSlidePage> slides) {
}
