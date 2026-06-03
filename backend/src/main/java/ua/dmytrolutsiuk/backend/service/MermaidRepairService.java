package ua.dmytrolutsiuk.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.client.mermaidvalidator.MermaidValidationRequest;
import ua.dmytrolutsiuk.backend.client.mermaidvalidator.MermaidValidationResponse;
import ua.dmytrolutsiuk.backend.client.mermaidvalidator.MermaidValidatorClient;
import ua.dmytrolutsiuk.backend.config.properties.GenerationProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.ProjectArtifacts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates every generated Mermaid diagram (the two C4 diagrams and each sequence diagram) against
 * the mermaid-validator microservice and repairs invalid ones with the LLM, feeding the validator's
 * error back in. Re-validates only the repaired diagrams each round, up to a configurable number of
 * attempts. If any diagram is still invalid after the last attempt it throws, which fails the
 * project (status FAILED).
 */
@Service
public class MermaidRepairService {

    private static final String C4_CONTEXT_ID = "c4-context";
    private static final String C4_CONTAINER_ID = "c4-container";
    private static final String SEQUENCE_ID_PREFIX = "seq:";

    private final MermaidValidatorClient validatorClient;
    private final LlmChatService llmChatService;
    private final GenerationProperties properties;
    private final String systemPrompt;

    public MermaidRepairService(
            MermaidValidatorClient validatorClient,
            LlmChatService llmChatService,
            GenerationProperties properties,
            @Value("classpath:prompts/mermaid-repair.md") Resource promptResource
    ) {
        this.validatorClient = validatorClient;
        this.llmChatService = llmChatService;
        this.properties = properties;
        try {
            this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load mermaid-repair.md", e);
        }
    }

    public ProjectArtifacts validateAndRepair(LlmModel model, ProjectArtifacts artifacts) {
        Map<String, String> codes = collectDiagrams(artifacts);

        Map<String, String> invalid = validate(codes, codes.keySet().stream().toList());
        int attempts = 0;
        int maxAttempts = Math.max(1, properties.mermaidRepairMaxAttempts());
        while (!invalid.isEmpty() && attempts < maxAttempts) {
            for (Map.Entry<String, String> entry : invalid.entrySet()) {
                codes.put(entry.getKey(), repair(model, codes.get(entry.getKey()), entry.getValue()));
            }
            attempts++;
            invalid = validate(codes, invalid.keySet().stream().toList());
        }

        if (!invalid.isEmpty()) {
            throw new IllegalStateException(
                    "Mermaid diagrams still invalid after " + maxAttempts + " repair attempt(s): " + invalid.keySet());
        }

        return rebuild(artifacts, codes);
    }

    private Map<String, String> collectDiagrams(ProjectArtifacts artifacts) {
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put(C4_CONTEXT_ID, requireCode(C4_CONTEXT_ID, artifacts.c4Context()));
        codes.put(C4_CONTAINER_ID, requireCode(C4_CONTAINER_ID, artifacts.c4Container()));
        for (ProjectArtifacts.SequenceDiagram diagram : artifacts.sequenceDiagrams()) {
            codes.put(SEQUENCE_ID_PREFIX + diagram.id(), requireCode(diagram.id(), diagram.code()));
        }
        return codes;
    }

    private Map<String, String> validate(Map<String, String> codes, List<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MermaidValidationRequest.Diagram> diagrams = ids.stream()
                .map(id -> new MermaidValidationRequest.Diagram(id, codes.get(id)))
                .toList();
        MermaidValidationResponse response = validatorClient.validate(new MermaidValidationRequest(diagrams));

        Map<String, String> invalid = new LinkedHashMap<>();
        for (MermaidValidationResponse.DiagramResult result : response.results()) {
            if (!result.valid()) {
                invalid.put(result.id(), result.error() == null ? "Invalid Mermaid syntax" : result.error());
            }
        }
        return invalid;
    }

    private String repair(LlmModel model, String code, String error) {
        String userMessage = PromptText.tag("diagram", code) + "\n" + PromptText.tag("validator_error", error);
        return PromptText.stripFences(
                llmChatService.callForText(model, systemPrompt, userMessage, properties.maxTokens().diagram()));
    }

    private ProjectArtifacts rebuild(ProjectArtifacts artifacts, Map<String, String> codes) {
        List<ProjectArtifacts.SequenceDiagram> sequenceDiagrams = artifacts.sequenceDiagrams().stream()
                .map(diagram -> new ProjectArtifacts.SequenceDiagram(
                        diagram.id(), diagram.title(), codes.get(SEQUENCE_ID_PREFIX + diagram.id())))
                .toList();
        return new ProjectArtifacts(
                codes.get(C4_CONTEXT_ID),
                codes.get(C4_CONTAINER_ID),
                artifacts.sdd(),
                artifacts.adrs(),
                sequenceDiagrams
        );
    }

    private static String requireCode(String id, String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Generated Mermaid diagram is empty: " + id);
        }
        return code;
    }
}
