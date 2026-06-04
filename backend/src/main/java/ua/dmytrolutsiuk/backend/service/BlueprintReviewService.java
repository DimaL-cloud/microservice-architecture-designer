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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reviews and fixes the freshly generated {@link ArchitectureBlueprint} before any artifact is
 * derived from it. A single structured-output LLM call (the second and last {@code .entity} call in
 * the pipeline, alongside {@link BlueprintService}) checks the blueprint against the brief's
 * requirements and microservice best practice (shared-database and other antipatterns) and returns a
 * corrected blueprint. The result is re-clamped to the ADR/flow caps and its referential integrity is
 * verified.
 *
 * <p>This is a hard quality gate: any failure (LLM error, or a corrected blueprint with
 * dangling/duplicate ids) throws, which the orchestrator turns into a {@code FAILED} project rather
 * than silently shipping an unreviewed blueprint.
 */
@Service
public class BlueprintReviewService {

    private final LlmChatService llmChatService;
    private final JsonCodec jsonCodec;
    private final GenerationProperties properties;
    private final String systemPrompt;

    public BlueprintReviewService(
            LlmChatService llmChatService,
            JsonCodec jsonCodec,
            GenerationProperties properties,
            @Value("classpath:prompts/blueprint-review.md") Resource promptResource
    ) {
        this.llmChatService = llmChatService;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load blueprint-review.md", e);
        }
    }

    public ArchitectureBlueprint review(LlmModel model, ProjectBrief brief, ArchitectureBlueprint blueprint) {
        String userMessage =
                PromptText.tag("project_brief", jsonCodec.writePretty(brief)) + "\n" +
                        PromptText.tag("architecture_blueprint", jsonCodec.writePretty(blueprint));
        ArchitectureBlueprint reviewed = llmChatService.call(
                model, systemPrompt, userMessage, properties.maxTokens().blueprint(), ArchitectureBlueprint.class);
        reviewed = BlueprintClamp.clamp(reviewed, properties.maxAdrs(), properties.maxFlows());
        validateReferentialIntegrity(reviewed);
        return reviewed;
    }

    /**
     * Fails fast if the reviewed blueprint is not self-consistent: ids must be unique across actors
     * and containers, and every relationship endpoint and key-flow participant must resolve to a
     * defined id. The downstream C4/sequence generators assume this holds, so a violation here is
     * treated as a generation failure (see class Javadoc).
     */
    static void validateReferentialIntegrity(ArchitectureBlueprint blueprint) {
        Set<String> ids = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (ArchitectureBlueprint.Actor actor : nullSafe(blueprint.actors())) {
            if (!ids.add(actor.id())) {
                duplicates.add(actor.id());
            }
        }
        for (ArchitectureBlueprint.Container container : nullSafe(blueprint.containers())) {
            if (!ids.add(container.id())) {
                duplicates.add(container.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("Reviewed blueprint has duplicate ids: " + duplicates);
        }

        List<String> dangling = new ArrayList<>();
        for (ArchitectureBlueprint.Relationship relationship : nullSafe(blueprint.relationships())) {
            if (!ids.contains(relationship.fromId())) {
                dangling.add("relationship.fromId=" + relationship.fromId());
            }
            if (!ids.contains(relationship.toId())) {
                dangling.add("relationship.toId=" + relationship.toId());
            }
        }
        for (ArchitectureBlueprint.Flow flow : nullSafe(blueprint.keyFlows())) {
            for (String participantId : nullSafe(flow.participantIds())) {
                if (!ids.contains(participantId)) {
                    dangling.add("keyFlow[" + flow.id() + "].participantId=" + participantId);
                }
            }
        }
        if (!dangling.isEmpty()) {
            throw new IllegalStateException("Reviewed blueprint references undefined ids: " + dangling);
        }
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
