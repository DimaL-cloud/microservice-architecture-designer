package ua.dmytrolutsiuk.backend.payload.request;

import ua.dmytrolutsiuk.backend.model.ProjectBrief;

import java.util.List;

/**
 * Payload of the "Save and Generate" action. {@code answers} carries the intake (structured-input)
 * answers and the follow-up question answers merged into one list — the complete brief the
 * architecture is generated from.
 */
public record SaveAndGenerateRequest(
        String name,
        String description,
        String llmModelId,
        List<ProjectBrief.Answer> answers
) {
}
