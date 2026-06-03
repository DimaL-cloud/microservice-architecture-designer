package ua.dmytrolutsiuk.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.config.properties.GenerationProperties;
import ua.dmytrolutsiuk.backend.model.ArchitectureBlueprint;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.ProjectBrief;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Produces the canonical {@link ArchitectureBlueprint} from the project brief — the first and only
 * structured-output call. Every later artifact is grounded on this object so they stay consistent.
 * Decision/flow counts are clamped so generation can't fan out unboundedly.
 */
@Service
public class BlueprintService {

    private final LlmChatService llmChatService;
    private final JsonCodec jsonCodec;
    private final GenerationProperties properties;
    private final String systemPrompt;

    public BlueprintService(
            LlmChatService llmChatService,
            JsonCodec jsonCodec,
            GenerationProperties properties,
            @Value("classpath:prompts/blueprint.md") Resource promptResource
    ) {
        this.llmChatService = llmChatService;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load blueprint.md", e);
        }
    }

    public ArchitectureBlueprint generate(LlmModel model, ProjectBrief brief) {
        String userMessage = PromptText.tag("project_brief", jsonCodec.writePretty(brief));
        ArchitectureBlueprint blueprint = llmChatService.call(
                model, systemPrompt, userMessage, properties.maxTokens().blueprint(), ArchitectureBlueprint.class);
        return clamp(blueprint);
    }

    private ArchitectureBlueprint clamp(ArchitectureBlueprint blueprint) {
        List<ArchitectureBlueprint.Decision> decisions = limit(blueprint.decisions(), properties.maxAdrs());
        List<ArchitectureBlueprint.Flow> flows = limit(blueprint.keyFlows(), properties.maxFlows());
        if (decisions == blueprint.decisions() && flows == blueprint.keyFlows()) {
            return blueprint;
        }
        return new ArchitectureBlueprint(
                blueprint.systemName(),
                blueprint.systemOverview(),
                blueprint.actors(),
                blueprint.containers(),
                blueprint.relationships(),
                decisions,
                flows
        );
    }

    private static <T> List<T> limit(List<T> values, int max) {
        if (values == null || values.size() <= max) {
            return values;
        }
        return List.copyOf(values.subList(0, max));
    }
}
