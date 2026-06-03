package ua.dmytrolutsiuk.backend.model;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * The full intake a user provided for a project: the project basics plus every structured-input
 * and follow-up answer. This is persisted (as jsonb) and is the single source the prompts are built
 * from, so artifact generation can be restarted without re-asking the user anything.
 */
public record ProjectBrief(
        String name,
        String description,
        String llmModelId,
        List<Answer> answers
) {

    public record Answer(
            String id,
            String label,
            JsonNode value
    ) {
    }
}
