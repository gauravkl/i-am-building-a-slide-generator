package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"x", "y", "w", "h"})
public record Bounds(int x, int y, int w, int h) {
}
