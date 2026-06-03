package ua.dmytrolutsiuk.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.payload.response.ProjectDetailResponse;

/**
 * Builds the single-project detail response, parsing the stored artifact JSON into a tree so it is
 * sent to the frontend as a structured object (rather than an escaped string).
 */
@Component
@RequiredArgsConstructor
public class ProjectResponseAssembler {

    private final JsonCodec jsonCodec;

    public ProjectDetailResponse toDetailResponse(Project project) {
        JsonNode artifacts = project.getArtifacts() == null ? null : jsonCodec.tree(project.getArtifacts());
        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getSummary(),
                project.getStatus(),
                project.getLlmModelId(),
                project.getCreatedAt(),
                project.getGenerationError(),
                artifacts
        );
    }
}
