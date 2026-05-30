package ua.dmytrolutsiuk.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ua.dmytrolutsiuk.backend.config.properties.LlmModelProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.model.ProjectBrief;
import ua.dmytrolutsiuk.backend.model.ProjectStatus;
import ua.dmytrolutsiuk.backend.payload.request.SaveAndGenerateRequest;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;

import java.util.List;

/**
 * Entry point for "Save and Generate" and "Restart generation". Saves the project synchronously
 * (including a freshly generated summary) and then kicks off the async artifact-generation
 * pipeline, returning immediately without waiting for generation to finish.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectGenerationService {

    private final ProjectRepository projectRepository;
    private final LlmModelProperties llmModelProperties;
    private final JsonCodec jsonCodec;
    private final ProjectSummaryService projectSummaryService;
    private final ArtifactGenerationOrchestrator orchestrator;

    /**
     * Saves the project with a generated summary and {@code GENERATING} status, then starts async
     * generation. The summary is generated inline so the project-list card shows it immediately;
     * if summarization fails it is left empty and generation still proceeds.
     */
    public Project saveAndGenerate(SaveAndGenerateRequest request) {
        LlmModel model = llmModelProperties.findById(request.llmModelId());
        ProjectBrief brief = new ProjectBrief(
                request.name(),
                request.description(),
                request.llmModelId(),
                request.answers() == null ? List.of() : request.answers()
        );

        String summary = generateSummary(model, brief);

        Project project = Project.builder()
                .name(request.name())
                .summary(summary)
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

    private String generateSummary(LlmModel model, ProjectBrief brief) {
        try {
            return projectSummaryService.summarize(model, brief);
        } catch (Exception e) {
            log.warn("Summary generation failed for project '{}'; saving without a summary: {}",
                    brief.name(), e.getMessage());
            return null;
        }
    }
}
