package ua.dmytrolutsiuk.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.dmytrolutsiuk.backend.mapper.ProjectMapper;
import ua.dmytrolutsiuk.backend.payload.response.ProjectResponse;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectMapper.toResponses(projectRepository.findAllByOrderByCreatedAtDesc());
    }
}
