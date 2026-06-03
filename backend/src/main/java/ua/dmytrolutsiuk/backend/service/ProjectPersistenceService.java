package ua.dmytrolutsiuk.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.dmytrolutsiuk.backend.model.ArchitectureBlueprint;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.model.ProjectArtifacts;
import ua.dmytrolutsiuk.backend.model.ProjectBrief;
import ua.dmytrolutsiuk.backend.model.ProjectStatus;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;

/**
 * Short, self-contained transactions used by the async generation flow. Each method opens and
 * commits its own transaction so that no JPA transaction is ever held across a long LLM/HTTP call.
 * The orchestrator runs on a background thread and therefore reloads the entity by id in every step.
 */
@Service
@RequiredArgsConstructor
public class ProjectPersistenceService {

    /** Generation errors can be long stack messages; keep the stored value bounded. */
    private static final int MAX_ERROR_LENGTH = 4000;

    private final ProjectRepository projectRepository;
    private final JsonCodec jsonCodec;

    @Transactional(readOnly = true)
    public ProjectBrief loadBrief(Long projectId) {
        Project project = require(projectId);
        if (project.getBrief() == null) {
            throw new IllegalStateException("Project " + projectId + " has no saved brief");
        }
        return jsonCodec.read(project.getBrief(), ProjectBrief.class);
    }

    /** Returns the project's current summary (may be the placeholder, the real one, or {@code null}). */
    @Transactional(readOnly = true)
    public String loadSummary(Long projectId) {
        return require(projectId).getSummary();
    }

    /** Returns the previously generated blueprint, or {@code null} if none has been persisted yet. */
    @Transactional(readOnly = true)
    public ArchitectureBlueprint loadBlueprint(Long projectId) {
        Project project = require(projectId);
        return project.getBlueprint() == null
                ? null
                : jsonCodec.read(project.getBlueprint(), ArchitectureBlueprint.class);
    }

    @Transactional
    public void saveBlueprint(Long projectId, ArchitectureBlueprint blueprint) {
        Project project = require(projectId);
        project.setBlueprint(jsonCodec.write(blueprint));
        projectRepository.save(project);
    }

    /**
     * Replaces the placeholder summary set at save time with the asynchronously generated one
     * (or {@code null} when summarization failed). Does not touch generation status.
     */
    @Transactional
    public void saveSummary(Long projectId, String summary) {
        Project project = require(projectId);
        project.setSummary(summary);
        projectRepository.save(project);
    }

    @Transactional
    public void completeReady(Long projectId, ProjectArtifacts artifacts) {
        Project project = require(projectId);
        project.setArtifacts(jsonCodec.write(artifacts));
        project.setGenerationError(null);
        project.setStatus(ProjectStatus.READY);
        projectRepository.save(project);
    }

    @Transactional
    public void markFailed(Long projectId, String error) {
        Project project = require(projectId);
        project.setStatus(ProjectStatus.FAILED);
        project.setGenerationError(truncate(error));
        projectRepository.save(project);
    }

    private Project require(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));
    }

    private static String truncate(String error) {
        if (error == null) {
            return "Generation failed";
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
