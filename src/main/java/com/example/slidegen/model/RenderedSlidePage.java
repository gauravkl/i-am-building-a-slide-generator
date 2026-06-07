package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"id", "type", "objects"})
public record RenderedSlidePage(String id, String type, List<RenderObject> objects) {
}
