package ua.dmytrolutsiuk.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.dmytrolutsiuk.backend.mapper.ProjectMapper;
import ua.dmytrolutsiuk.backend.payload.request.GenerateQuestionsRequest;
import ua.dmytrolutsiuk.backend.payload.response.GeneratedQuestionsResponse;
import ua.dmytrolutsiuk.backend.payload.response.ProjectResponse;
import ua.dmytrolutsiuk.backend.repository.ProjectRepository;
import ua.dmytrolutsiuk.backend.service.QuestionGenerationService;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final QuestionGenerationService questionGenerationService;

    @GetMapping
    public List<ProjectResponse> getAllProjects() {
        return projectMapper.toResponses(projectRepository.findAllByOrderByCreatedAtDesc());
    }

    @PostMapping("/questions")
    public GeneratedQuestionsResponse generateQuestions(@RequestBody GenerateQuestionsRequest request) {
        return questionGenerationService.generate(request);
    }
}
