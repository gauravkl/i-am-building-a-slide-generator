package com.example.slidegen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"fontSize", "fontWeight", "color", "fillColor", "strokeColor", "strokeWidth"})
public record Style(
        Integer fontSize,
        Integer fontWeight,
        String color,
        String fillColor,
        String strokeColor,
        Integer strokeWidth
) {
    public Style(Integer fontSize, Integer fontWeight) {
        this(fontSize, fontWeight, null, null, null, null);
    }
}
