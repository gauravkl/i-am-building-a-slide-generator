package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"type", "size", "slides"})
public record DeckInput(String type, SlideSize size, List<SlideSpec> slides) {
}
