package ua.dmytrolutsiuk.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.model.ProjectStatus;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findByStatus(ProjectStatus status);
}
