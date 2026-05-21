package ua.dmytrolutsiuk.backend.model;

public record LlmModel(
        String id,
        String name,
        LlmProvider provider
) {
}
