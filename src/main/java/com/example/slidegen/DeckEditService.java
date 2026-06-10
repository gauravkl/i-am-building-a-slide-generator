package com.example.slidegen;

import com.example.slidegen.model.Bounds;
import com.example.slidegen.model.DeckEditOperation;
import com.example.slidegen.model.DeckEditPatch;
import com.example.slidegen.model.DeckInput;
import com.example.slidegen.model.SlideComponent;
import com.example.slidegen.model.SlideSpec;
import com.example.slidegen.model.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeckEditService {
    private static final int MAX_EDIT_ATTEMPTS = 2;

    private final SlideEditClient slideEditClient;
    private final ComponentDeckRenderer deckRenderer;

    public DeckEditService(SlideEditClient slideEditClient, ComponentDeckRenderer deckRenderer) {
        this.slideEditClient = slideEditClient == null ? SlideEditClient.disabled() : slideEditClient;
        this.deckRenderer = deckRenderer == null ? new ComponentDeckRenderer() : deckRenderer;
    }

    public DeckInput edit(DeckInput deckInput, String instructionText) throws java.io.IOException, InterruptedException {
        if (instructionText == null || instructionText.isBlank()) {
            throw new SlideLayoutException("Edit instruction is required.");
        }
        deckRenderer.validateForAssetGeneration(deckInput);

        SlideLayoutException lastValidationError = null;
        for (int attempt = 0; attempt < MAX_EDIT_ATTEMPTS; attempt++) {
            String effectiveInstruction = attempt == 0
                    ? instructionText
                    : correctionInstruction(instructionText, lastValidationError);
            DeckEditPatch patch = slideEditClient.generatePatch(deckInput, effectiveInstruction);
            try {
                DeckInput editedDeckInput = applyPatch(deckInput, patch);
                deckRenderer.render(editedDeckInput);
                return editedDeckInput;
            } catch (SlideLayoutException ex) {
                lastValidationError = ex;
            }
        }

        throw lastValidationError == null
                ? new SlideLayoutException("Generated edit patch failed validation.")
                : lastValidationError;
    }

    private static String correctionInstruction(String instructionText, SlideLayoutException validationError) {
        return instructionText + """

                Correction from the Java edit patch validator:
                """
                + validationError.getMessage()
                + """

                Return a valid DeckEditPatch. Only target existing slideId/componentId pairs.
                Keep every updated component inside the slide bounds.
                Return null for unchanged optional update fields.
                """;
    }

    DeckInput applyPatch(DeckInput deckInput, DeckEditPatch patch) {
        validatePatch(patch);

        Map<String, SlideSpec> slidesById = new HashMap<>();
        Map<Target, DeckEditOperation> operationsByTarget = new HashMap<>();
        Set<Target> seenTargets = new HashSet<>();
        for (SlideSpec slide : deckInput.slides()) {
            slidesById.put(slide.id(), slide);
        }

        for (DeckEditOperation operation : patch.operations()) {
            validateOperationShape(operation);
            Target target = new Target(operation.slideId(), operation.componentId());
            if (!seenTargets.add(target)) {
                throw new SlideLayoutException("Edit patch contains duplicate operations for component: "
                        + operation.slideId()
                        + "/"
                        + operation.componentId());
            }
            SlideSpec slide = slidesById.get(operation.slideId());
            if (slide == null) {
                throw new SlideLayoutException("Edit patch targets missing slide: " + operation.slideId());
            }
            if (componentById(slide, operation.componentId()) == null) {
                throw new SlideLayoutException("Edit patch targets missing component: "
                        + operation.slideId()
                        + "/"
                        + operation.componentId());
            }
            operationsByTarget.put(target, operation);
        }

        List<SlideSpec> editedSlides = new ArrayList<>();
        for (SlideSpec slide : deckInput.slides()) {
            List<SlideComponent> editedComponents = new ArrayList<>();
            for (SlideComponent component : slide.components()) {
                DeckEditOperation operation = operationsByTarget.get(new Target(slide.id(), component.id()));
                editedComponents.add(operation == null ? component : applyOperation(component, operation));
            }
            editedSlides.add(new SlideSpec(slide.id(), slide.type(), List.copyOf(editedComponents)));
        }

        DeckInput editedDeckInput = new DeckInput(deckInput.type(), deckInput.size(), List.copyOf(editedSlides));
        deckRenderer.validateForAssetGeneration(editedDeckInput);
        return editedDeckInput;
    }

    private static void validatePatch(DeckEditPatch patch) {
        if (patch == null) {
            throw new SlideLayoutException("Edit patch is required.");
        }
        if (!"DeckEditPatch".equals(patch.type())) {
            throw new SlideLayoutException("Expected edit patch type to be 'DeckEditPatch'.");
        }
        if (patch.operations() == null || patch.operations().isEmpty()) {
            throw new SlideLayoutException("Edit patch must contain at least one operation.");
        }
    }

    private static void validateOperationShape(DeckEditOperation operation) {
        if (operation == null) {
            throw new SlideLayoutException("Edit patch operations cannot contain null entries.");
        }
        if (!"updateComponent".equals(operation.op())) {
            throw new SlideLayoutException("Unsupported edit operation: " + operation.op());
        }
        if (operation.slideId() == null || operation.slideId().isBlank()) {
            throw new SlideLayoutException("Edit operation requires slideId.");
        }
        if (operation.componentId() == null || operation.componentId().isBlank()) {
            throw new SlideLayoutException("Edit operation requires componentId.");
        }
        if (operation.type() == null
                && operation.bounds() == null
                && !hasStyleUpdates(operation.style())
                && !hasContentUpdates(operation)) {
            throw new SlideLayoutException("Edit operation must update type, bounds, style, or content: "
                    + operation.slideId()
                    + "/"
                    + operation.componentId());
        }
    }

    private static SlideComponent componentById(SlideSpec slide, String componentId) {
        for (SlideComponent component : slide.components()) {
            if (componentId.equals(component.id())) {
                return component;
            }
        }
        return null;
    }

    private static SlideComponent applyOperation(SlideComponent component, DeckEditOperation operation) {
        String finalType = operation.type() == null ? component.type() : operation.type();
        boolean preserveExistingContent = finalType.equals(component.type());
        Bounds finalBounds = operation.bounds() == null ? component.bounds() : operation.bounds();
        Style finalStyle = mergeStyle(component.style(), operation.style());

        return switch (finalType) {
            case "text" -> new SlideComponent(
                    component.id(),
                    finalType,
                    contentValue(operation.text(), component.text(), preserveExistingContent),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
            case "bullets" -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    contentValue(operation.items(), component.items(), preserveExistingContent),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
            case "matrix" -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    null,
                    null,
                    contentValue(operation.rows(), component.rows(), preserveExistingContent),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
            case "table" -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    null,
                    contentValue(operation.headers(), component.headers(), preserveExistingContent),
                    contentValue(operation.rows(), component.rows(), preserveExistingContent),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
            case "image" -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    null,
                    null,
                    null,
                    contentValue(operation.imagePrompt(), component.imagePrompt(), preserveExistingContent),
                    contentValue(operation.src(), component.src(), preserveExistingContent),
                    contentValue(operation.alt(), component.alt(), preserveExistingContent),
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
            case "chart" -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    contentValue(operation.chartType(), component.chartType(), preserveExistingContent),
                    contentValue(operation.labels(), component.labels(), preserveExistingContent),
                    contentValue(operation.values(), component.values(), preserveExistingContent),
                    finalBounds,
                    finalStyle
            );
            case "rect", "circle", "arrow" -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
            default -> new SlideComponent(
                    component.id(),
                    finalType,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    finalBounds,
                    finalStyle
            );
        };
    }

    private static <T> T contentValue(T patchValue, T existingValue, boolean preserveExistingContent) {
        if (patchValue != null) {
            return patchValue;
        }
        return preserveExistingContent ? existingValue : null;
    }

    private static Style mergeStyle(Style existing, Style patch) {
        if (!hasStyleUpdates(patch)) {
            return existing;
        }
        if (existing == null) {
            return patch;
        }
        return new Style(
                patch.fontSize() == null ? existing.fontSize() : patch.fontSize(),
                patch.fontWeight() == null ? existing.fontWeight() : patch.fontWeight(),
                patch.color() == null ? existing.color() : patch.color(),
                patch.fillColor() == null ? existing.fillColor() : patch.fillColor(),
                patch.strokeColor() == null ? existing.strokeColor() : patch.strokeColor(),
                patch.strokeWidth() == null ? existing.strokeWidth() : patch.strokeWidth()
        );
    }

    private static boolean hasStyleUpdates(Style style) {
        return style != null
                && (style.fontSize() != null
                || style.fontWeight() != null
                || style.color() != null
                || style.fillColor() != null
                || style.strokeColor() != null
                || style.strokeWidth() != null);
    }

    private static boolean hasContentUpdates(DeckEditOperation operation) {
        return operation.text() != null
                || operation.items() != null
                || operation.headers() != null
                || operation.rows() != null
                || operation.imagePrompt() != null
                || operation.src() != null
                || operation.alt() != null
                || operation.chartType() != null
                || operation.labels() != null
                || operation.values() != null;
    }

    private record Target(String slideId, String componentId) {
    }
}
