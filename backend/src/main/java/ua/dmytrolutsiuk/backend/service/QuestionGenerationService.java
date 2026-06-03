package ua.dmytrolutsiuk.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import ua.dmytrolutsiuk.backend.config.properties.LlmModelProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.payload.request.GenerateQuestionsRequest;
import ua.dmytrolutsiuk.backend.payload.response.GeneratedQuestionsResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionGenerationService {

    private final LlmModelProperties llmModelProperties;
    private final LlmChatService llmChatService;
    private final JsonMapper jsonMapper;
    private final String systemPrompt;

    public QuestionGenerationService(
            LlmModelProperties llmModelProperties,
            LlmChatService llmChatService,
            JsonMapper jsonMapper,
            @Value("classpath:prompts/generate-questions.md") Resource promptResource
    ) {
        this.llmModelProperties = llmModelProperties;
        this.llmChatService = llmChatService;
        this.jsonMapper = jsonMapper;
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load generate-questions.md", e);
        }
    }

    public GeneratedQuestionsResponse generate(GenerateQuestionsRequest request) {
        LlmModel model = llmModelProperties.findById(request.llmModelId());
        return llmChatService.call(
                model,
                systemPrompt,
                buildUserMessage(request),
                GeneratedQuestionsResponse.class
        );
    }

    private String buildUserMessage(GenerateQuestionsRequest request) {
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("name", request.name());
        brief.put("description", request.description());
        brief.put("llmModelId", request.llmModelId());
        brief.put("answers", request.answers() == null ? List.of() : request.answers());
        try {
            String json = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(brief);
            return "<project_brief>\n" + json + "\n</project_brief>";
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize project brief", e);
        }
    }
}
