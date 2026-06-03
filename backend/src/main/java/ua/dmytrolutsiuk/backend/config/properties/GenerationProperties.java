package ua.dmytrolutsiuk.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for artifact generation. Bound from the {@code generation.*} block in application.yml.
 */
@ConfigurationProperties(prefix = "generation")
public record GenerationProperties(

        /** Max number of times an invalid Mermaid diagram is sent back to the LLM to be repaired. */
        @DefaultValue("3") int mermaidRepairMaxAttempts,

        /** Max concurrent per-artifact LLM calls (bounds throughput vs provider rate limits). */
        @DefaultValue("3") int concurrency,

        /** Upper bound on generated ADRs (one per blueprint decision). */
        @DefaultValue("8") int maxAdrs,

        /** Upper bound on generated sequence diagrams (one per blueprint key flow). */
        @DefaultValue("6") int maxFlows,

        @DefaultValue MaxTokens maxTokens
) {

    /** Per-artifact output-token caps. SDD is the largest; the brief summary the smallest. */
    public record MaxTokens(
            @DefaultValue("512") int summary,
            @DefaultValue("32000") int blueprint,
            @DefaultValue("16000") int sdd,
            @DefaultValue("8000") int diagram,
            @DefaultValue("6000") int adr
    ) {
    }
}
