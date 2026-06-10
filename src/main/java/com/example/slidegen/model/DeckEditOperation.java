package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"op", "slideId", "componentId", "type", "text", "items", "headers", "rows", "imagePrompt", "src", "alt", "chartType", "labels", "values", "bounds", "style"})
public record DeckEditOperation(
        String op,
        String slideId,
        String componentId,
        String type,
        String text,
        List<String> items,
        List<String> headers,
        List<List<String>> rows,
        String imagePrompt,
        String src,
        String alt,
        String chartType,
        List<String> labels,
        List<Double> values,
        Bounds bounds,
        Style style
) {
    public DeckEditOperation(
            String op,
            String slideId,
            String componentId,
            String type,
            Bounds bounds,
            Style style
    ) {
        this(op, slideId, componentId, type, null, null, null, null, null, null, null, null, null, null, bounds, style);
    }
}
