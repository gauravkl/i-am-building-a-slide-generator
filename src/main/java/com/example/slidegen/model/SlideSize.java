package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"width", "height"})
public record SlideSize(int width, int height) {
}
