package ua.dmytrolutsiuk.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.config.properties.GenerationProperties;
import ua.dmytrolutsiuk.backend.model.ArchitectureBlueprint;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.ProjectArtifacts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Generates every artifact in its own LLM call (so each stays within the output-token limit), all
 * grounded on the same blueprint for consistency. Calls fan out onto a bounded executor so
 * throughput is capped to avoid provider rate limits.
 */
@Service
public class ArtifactGenerationService {

    private final LlmChatService llmChatService;
    private final JsonCodec jsonCodec;
    private final GenerationProperties properties;
    private final Executor executor;

    private final String c4ContextPrompt;
    private final String c4ContainerPrompt;
    private final String sddPrompt;
    private final String adrPrompt;
    private final String sequenceDiagramPrompt;

    public ArtifactGenerationService(
            LlmChatService llmChatService,
            JsonCodec jsonCodec,
            GenerationProperties properties,
            @Qualifier("artifactCallExecutor") Executor executor,
            @Value("classpath:prompts/c4-context.md") Resource c4ContextPrompt,
            @Value("classpath:prompts/c4-container.md") Resource c4ContainerPrompt,
            @Value("classpath:prompts/sdd.md") Resource sddPrompt,
            @Value("classpath:prompts/adr.md") Resource adrPrompt,
            @Value("classpath:prompts/sequence-diagram.md") Resource sequenceDiagramPrompt
    ) {
        this.llmChatService = llmChatService;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
        this.executor = executor;
        this.c4ContextPrompt = read(c4ContextPrompt, "c4-context.md");
        this.c4ContainerPrompt = read(c4ContainerPrompt, "c4-container.md");
        this.sddPrompt = read(sddPrompt, "sdd.md");
        this.adrPrompt = read(adrPrompt, "adr.md");
        this.sequenceDiagramPrompt = read(sequenceDiagramPrompt, "sequence-diagram.md");
    }

    public ProjectArtifacts generateAll(LlmModel model, ArchitectureBlueprint blueprint) {
        String blueprintTag = PromptText.tag("architecture_blueprint", jsonCodec.writePretty(blueprint));
        GenerationProperties.MaxTokens tokens = properties.maxTokens();

        CompletableFuture<String> c4Context = supply(() ->
                PromptText.stripFences(llmChatService.callForText(model, c4ContextPrompt, blueprintTag, tokens.diagram())));
        CompletableFuture<String> c4Container = supply(() ->
                PromptText.stripFences(llmChatService.callForText(model, c4ContainerPrompt, blueprintTag, tokens.diagram())));
        CompletableFuture<String> sdd = supply(() ->
                PromptText.stripFences(llmChatService.callForText(model, sddPrompt, blueprintTag, tokens.sdd())));

        List<CompletableFuture<ProjectArtifacts.Adr>> adrFutures = blueprint.decisions().stream()
                .map(decision -> supply(() -> {
                    String userMessage = blueprintTag + "\n" + PromptText.tag("target_decision", jsonCodec.writePretty(decision));
                    String markdown = PromptText.stripFences(
                            llmChatService.callForText(model, adrPrompt, userMessage, tokens.adr()));
                    return new ProjectArtifacts.Adr(decision.id(), decision.title(), markdown);
                }))
                .toList();

        List<CompletableFuture<ProjectArtifacts.SequenceDiagram>> sequenceFutures = blueprint.keyFlows().stream()
                .map(flow -> supply(() -> {
                    String userMessage = blueprintTag + "\n" + PromptText.tag("target_flow", jsonCodec.writePretty(flow));
                    String code = PromptText.stripFences(
                            llmChatService.callForText(model, sequenceDiagramPrompt, userMessage, tokens.diagram()));
                    return new ProjectArtifacts.SequenceDiagram(flow.id(), flow.title(), code);
                }))
                .toList();

        return new ProjectArtifacts(
                c4Context.join(),
                c4Container.join(),
                sdd.join(),
                adrFutures.stream().map(CompletableFuture::join).toList(),
                sequenceFutures.stream().map(CompletableFuture::join).toList()
        );
    }

    private <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private static String read(Resource resource, String name) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + name, e);
        }
    }
}
