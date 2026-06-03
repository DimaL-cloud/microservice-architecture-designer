package ua.dmytrolutsiuk.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.model.ProjectStatus;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;

import java.util.List;

/**
 * Generation runs in-memory, so a server restart mid-generation would leave a project stuck in
 * {@code GENERATING} forever. On startup, any such orphaned project is marked {@code FAILED} so the
 * user can restart it (the brief is persisted, so a restart re-runs cleanly).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenerationRecoveryListener {

    private static final String INTERRUPTED_MESSAGE =
            "Generation was interrupted by a server restart. Please restart generation.";

    private final ProjectRepository projectRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failOrphanedGenerations() {
        List<Project> orphaned = projectRepository.findByStatus(ProjectStatus.GENERATING);
        if (orphaned.isEmpty()) {
            return;
        }
        log.warn("Marking {} project(s) stuck in GENERATING as FAILED after restart", orphaned.size());
        for (Project project : orphaned) {
            project.setStatus(ProjectStatus.FAILED);
            project.setGenerationError(INTERRUPTED_MESSAGE);
            if (ProjectSummaryService.PLACEHOLDER.equals(project.getSummary())) {
                project.setSummary(null);
            }
        }
        projectRepository.saveAll(orphaned);
    }
}
