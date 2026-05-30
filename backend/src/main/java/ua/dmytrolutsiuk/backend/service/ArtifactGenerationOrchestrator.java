package ua.dmytrolutsiuk.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.config.properties.LlmModelProperties;
import ua.dmytrolutsiuk.backend.model.ArchitectureBlueprint;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.ProjectArtifacts;
import ua.dmytrolutsiuk.backend.model.ProjectBrief;

/**
 * Runs the full artifact-generation pipeline on a background thread: blueprint → per-artifact
 * generation → automated review → Mermaid validate/repair → persist + mark READY. Holds no database
 * transaction across the long LLM/HTTP work (each persistence step is its own short transaction).
 * Any failure marks the project FAILED so the user can restart it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactGenerationOrchestrator {

    private final ProjectPersistenceService persistenceService;
    private final LlmModelProperties llmModelProperties;
    private final BlueprintService blueprintService;
    private final ArtifactGenerationService artifactGenerationService;
    private final ArtifactReviewService artifactReviewService;
    private final MermaidRepairService mermaidRepairService;

    @Async("artifactGenerationExecutor")
    public void generate(Long projectId) {
        log.info("Starting artifact generation for project {}", projectId);
        try {
            ProjectBrief brief = persistenceService.loadBrief(projectId);
            LlmModel model = llmModelProperties.findById(brief.llmModelId());

            ArchitectureBlueprint blueprint = persistenceService.loadBlueprint(projectId);
            if (blueprint == null) {
                blueprint = blueprintService.generate(model, brief);
                persistenceService.saveBlueprint(projectId, blueprint);
            } else {
                log.info("Reusing persisted blueprint for project {}", projectId);
            }

            ProjectArtifacts artifacts = artifactGenerationService.generateAll(model, blueprint);
            artifacts = artifactReviewService.reviewAll(model, blueprint, artifacts);
            artifacts = mermaidRepairService.validateAndRepair(model, artifacts);

            persistenceService.completeReady(projectId, artifacts);
            log.info("Artifact generation completed for project {}", projectId);
        } catch (Exception e) {
            log.error("Artifact generation failed for project {}: {}", projectId, e.getMessage(), e);
            try {
                persistenceService.markFailed(projectId, e.getMessage());
            } catch (Exception markFailure) {
                log.error("Could not mark project {} as FAILED", projectId, markFailure);
            }
        }
    }
}
