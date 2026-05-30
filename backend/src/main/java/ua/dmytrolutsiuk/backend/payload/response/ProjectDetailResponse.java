package ua.dmytrolutsiuk.backend.payload.response;

import tools.jackson.databind.JsonNode;
import ua.dmytrolutsiuk.backend.model.ProjectStatus;

import java.time.Instant;

/**
 * Single-project view used for polling generation status and for sending the generated artifacts
 * to the frontend. {@code artifacts} is the raw artifact JSON (null until generation succeeds);
 * {@code generationError} is populated only when {@code status == FAILED}.
 */
public record ProjectDetailResponse(
        Long id,
        String name,
        String summary,
        ProjectStatus status,
        String llmModelId,
        Instant createdAt,
        String generationError,
        JsonNode artifacts
) {
}
