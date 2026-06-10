package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"id", "type", "text", "items", "headers", "rows", "imagePrompt", "src", "alt", "chartType", "labels", "values", "bounds", "style"})
public record SlideComponent(
        String id,
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
    public SlideComponent(
            String id,
            String type,
            String text,
            List<String> items,
            List<String> headers,
            List<List<String>> rows,
            Bounds bounds,
            Style style
    ) {
        this(id, type, text, items, headers, rows, null, null, null, null, null, null, bounds, style);
    }

    public SlideComponent(
            String id,
            String type,
            String text,
            List<String> items,
            List<String> headers,
            List<List<String>> rows,
            String imagePrompt,
            String src,
            String alt,
            Bounds bounds,
            Style style
    ) {
        this(id, type, text, items, headers, rows, imagePrompt, src, alt, null, null, null, bounds, style);
    }
}
