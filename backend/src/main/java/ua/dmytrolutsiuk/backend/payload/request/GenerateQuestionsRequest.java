package ua.dmytrolutsiuk.backend.payload.request;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record GenerateQuestionsRequest(
        String name,
        String description,
        String llmModelId,
        List<StructuredAnswer> answers
) {

    public record StructuredAnswer(
            String id,
            String label,
            JsonNode value
    ) {
    }
}
