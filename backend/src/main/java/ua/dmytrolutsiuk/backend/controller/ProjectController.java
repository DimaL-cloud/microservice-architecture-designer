package ua.dmytrolutsiuk.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ua.dmytrolutsiuk.backend.mapper.ProjectMapper;
import ua.dmytrolutsiuk.backend.model.Project;
import ua.dmytrolutsiuk.backend.payload.request.GenerateQuestionsRequest;
import ua.dmytrolutsiuk.backend.payload.request.SaveAndGenerateRequest;
import ua.dmytrolutsiuk.backend.payload.response.GeneratedQuestionsResponse;
import ua.dmytrolutsiuk.backend.payload.response.ProjectDetailResponse;
import ua.dmytrolutsiuk.backend.payload.response.ProjectResponse;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;
import ua.dmytrolutsiuk.backend.service.ProjectGenerationService;
import ua.dmytrolutsiuk.backend.service.ProjectResponseAssembler;
import ua.dmytrolutsiuk.backend.service.QuestionGenerationService;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ProjectResponseAssembler projectResponseAssembler;
    private final QuestionGenerationService questionGenerationService;
    private final ProjectGenerationService projectGenerationService;

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectMapper.toResponses(projectRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse getProject(@PathVariable Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return projectResponseAssembler.toDetailResponse(project);
    }

    @PostMapping("/questions")
    public GeneratedQuestionsResponse generateQuestions(@RequestBody GenerateQuestionsRequest request) {
        return questionGenerationService.generate(request);
    }

    /** Saves the project and starts asynchronous artifact generation; returns immediately. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDetailResponse saveAndGenerate(@RequestBody SaveAndGenerateRequest request) {
        Project project = projectGenerationService.saveAndGenerate(request);
        return projectResponseAssembler.toDetailResponse(project);
    }

    /** Re-runs generation for a previously failed project from its saved brief. */
    @PostMapping("/{id}/restart-generation")
    public ProjectDetailResponse restartGeneration(@PathVariable Long id) {
        Project project = projectGenerationService.restart(id);
        return projectResponseAssembler.toDetailResponse(project);
    }
}
