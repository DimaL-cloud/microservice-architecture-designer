package ua.dmytrolutsiuk.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ua.dmytrolutsiuk.backend.config.properties.LlmModelProperties;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.model.ProjectBrief;
import ua.dmytrolutsiuk.backend.model.ProjectStatus;
import ua.dmytrolutsiuk.backend.payload.request.SaveAndGenerateRequest;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;

import java.util.List;

/**
 * Entry point for "Save and Generate" and "Restart generation". Saves the project synchronously
 * with a placeholder summary and {@code GENERATING} status, then kicks off the async
 * artifact-generation pipeline (which also generates the real summary), returning immediately
 * without waiting for generation to finish.
 */
@Service
@RequiredArgsConstructor
public class ProjectGenerationService {

    private final ProjectRepository projectRepository;
    private final LlmModelProperties llmModelProperties;
    private final JsonCodec jsonCodec;
    private final ArtifactGenerationOrchestrator orchestrator;

    /**
     * Saves the project with a placeholder summary and {@code GENERATING} status, then starts async
     * generation. The real summary is generated on the background thread and replaces the
     * placeholder once ready (see {@link ArtifactGenerationOrchestrator}).
     */
    public Project saveAndGenerate(SaveAndGenerateRequest request) {
        // Validate the model id eagerly so an unknown id fails fast at save time rather than
        // surfacing only after the project has been persisted as GENERATING.
        llmModelProperties.findById(request.llmModelId());
        ProjectBrief brief = new ProjectBrief(
                request.name(),
                request.description(),
                request.llmModelId(),
                request.answers() == null ? List.of() : request.answers()
        );

        Project project = Project.builder()
                .name(request.name())
                .summary(ProjectSummaryService.PLACEHOLDER)
                .status(ProjectStatus.GENERATING)
                .llmModelId(request.llmModelId())
                .brief(jsonCodec.write(brief))
                .build();
        // save() runs in its own transaction and commits before we trigger the async pipeline,
        // so the background thread reliably sees the persisted row.
        Project saved = projectRepository.save(project);

        orchestrator.generate(saved.getId());
        return saved;
    }

    /**
     * Re-runs generation for a project (used after a failure) from its persisted brief — no user
     * input required. No-op if the project is already generating.
     */
    public Project restart(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (project.getStatus() == ProjectStatus.GENERATING) {
            return project;
        }
        project.setStatus(ProjectStatus.GENERATING);
        project.setGenerationError(null);
        Project saved = projectRepository.save(project);

        orchestrator.generate(saved.getId());
        return saved;
    }
}
