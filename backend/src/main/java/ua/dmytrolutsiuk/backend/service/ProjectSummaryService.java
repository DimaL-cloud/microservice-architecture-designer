package ua.dmytrolutsiuk.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.config.properties.GenerationProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.ProjectBrief;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Generates the one/two-sentence project summary shown on the project-list card. Called
 * synchronously when the user clicks "Save and Generate" so the summary is present immediately.
 */
@Service
public class ProjectSummaryService {

    private final LlmChatService llmChatService;
    private final JsonCodec jsonCodec;
    private final GenerationProperties properties;
    private final String systemPrompt;

    public ProjectSummaryService(
            LlmChatService llmChatService,
            JsonCodec jsonCodec,
            GenerationProperties properties,
            @Value("classpath:prompts/summarize.md") Resource promptResource
    ) {
        this.llmChatService = llmChatService;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
        this.systemPrompt = read(promptResource);
    }

    public String summarize(LlmModel model, ProjectBrief brief) {
        String userMessage = PromptText.tag("project_brief", jsonCodec.writePretty(brief));
        String summary = llmChatService.callForText(model, systemPrompt, userMessage, properties.maxTokens().summary());
        return summary == null ? null : summary.strip();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load summarize.md", e);
        }
    }
}
