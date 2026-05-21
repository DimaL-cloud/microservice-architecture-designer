package ua.dmytrolutsiuk.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.dmytrolutsiuk.backend.config.properties.LlmModelProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;

import java.util.List;

@RestController
@RequestMapping("/api/llm-models")
@RequiredArgsConstructor
public class LlmModelController {

    private final LlmModelProperties properties;

    @GetMapping
    public List<LlmModel> getLlmModels() {
        return properties.models();
    }
}
