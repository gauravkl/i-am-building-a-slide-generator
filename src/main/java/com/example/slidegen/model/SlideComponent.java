package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "type", "text", "items", "headers", "rows", "bounds", "style"})
public record SlideComponent(
        String id,
        String type,
        String text,
        List<String> items,
        List<String> headers,
        List<List<String>> rows,
        Bounds bounds,
        Style style
) {
}
