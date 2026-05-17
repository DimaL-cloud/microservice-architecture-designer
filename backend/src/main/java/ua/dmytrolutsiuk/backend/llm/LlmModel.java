package ua.dmytrolutsiuk.backend.llm;

public record LlmModel(
        String id,
        LlmProvider provider,
        String displayName,
        String description
) {
}
