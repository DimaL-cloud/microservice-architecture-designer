package ua.dmytrolutsiuk.backend.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/llm/models")
@RequiredArgsConstructor
public class LlmModelController {

    private final LlmModelService llmModelService;

    @GetMapping
    public List<LlmModel> list() {
        return llmModelService.getAvailableModels();
    }
}
