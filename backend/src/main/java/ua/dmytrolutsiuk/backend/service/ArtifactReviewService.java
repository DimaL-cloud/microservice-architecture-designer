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
 * Automated review-and-fix pass (no user involvement). Each artifact is reviewed in its own call —
 * staying within the output-token limit — grounded by the blueprint so any cross-artifact drift is
 * corrected toward the canonical model. The corrected artifact replaces the original.
 */
@Service
public class ArtifactReviewService {

    private final LlmChatService llmChatService;
    private final JsonCodec jsonCodec;
    private final GenerationProperties properties;
    private final Executor executor;
    private final String systemPrompt;

    public ArtifactReviewService(
            LlmChatService llmChatService,
            JsonCodec jsonCodec,
            GenerationProperties properties,
            @Qualifier("artifactCallExecutor") Executor executor,
            @Value("classpath:prompts/review.md") Resource promptResource
    ) {
        this.llmChatService = llmChatService;
        this.jsonCodec = jsonCodec;
        this.properties = properties;
        this.executor = executor;
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load review.md", e);
        }
    }

    public ProjectArtifacts reviewAll(LlmModel model, ArchitectureBlueprint blueprint, ProjectArtifacts artifacts,
                                      TokenUsageAccumulator accumulator) {
        String blueprintTag = PromptText.tag("architecture_blueprint", jsonCodec.writePretty(blueprint));
        GenerationProperties.MaxTokens tokens = properties.maxTokens();

        CompletableFuture<String> c4Context = supply(() ->
                review(model, blueprintTag, "C4_CONTEXT", artifacts.c4Context(), tokens.diagram(), accumulator));
        CompletableFuture<String> c4Container = supply(() ->
                review(model, blueprintTag, "C4_CONTAINER", artifacts.c4Container(), tokens.diagram(), accumulator));
        CompletableFuture<String> sdd = supply(() ->
                review(model, blueprintTag, "SDD", artifacts.sdd(), tokens.sdd(), accumulator));

        List<CompletableFuture<ProjectArtifacts.Adr>> adrFutures = artifacts.adrs().stream()
                .map(adr -> supply(() -> new ProjectArtifacts.Adr(
                        adr.id(),
                        adr.title(),
                        review(model, blueprintTag, "ADR", adr.markdown(), tokens.adr(), accumulator))))
                .toList();

        List<CompletableFuture<ProjectArtifacts.SequenceDiagram>> sequenceFutures = artifacts.sequenceDiagrams().stream()
                .map(diagram -> supply(() -> new ProjectArtifacts.SequenceDiagram(
                        diagram.id(),
                        diagram.title(),
                        review(model, blueprintTag, "SEQUENCE_DIAGRAM", diagram.code(), tokens.diagram(), accumulator))))
                .toList();

        return new ProjectArtifacts(
                c4Context.join(),
                c4Container.join(),
                sdd.join(),
                adrFutures.stream().map(CompletableFuture::join).toList(),
                sequenceFutures.stream().map(CompletableFuture::join).toList()
        );
    }

    private String review(LlmModel model, String blueprintTag, String type, String content, int maxTokens,
                          TokenUsageAccumulator accumulator) {
        String artifactTag = "<artifact type=\"" + type + "\">\n" + content + "\n</artifact>";
        String userMessage = blueprintTag + "\n" + artifactTag;
        return PromptText.stripFences(llmChatService.callForText(model, systemPrompt, userMessage, maxTokens, accumulator));
    }

    private <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }
}
