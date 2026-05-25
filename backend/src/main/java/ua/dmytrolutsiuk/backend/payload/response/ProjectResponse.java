package ua.dmytrolutsiuk.backend.payload.response;

import ua.dmytrolutsiuk.backend.model.ProjectStatus;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String summary,
        ProjectStatus status,
        String llmModelId,
        Instant createdAt
) {
}
