package ua.dmytrolutsiuk.backend.service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe running total of LLM token usage for a single generation run. One instance is created
 * by {@link ArtifactGenerationOrchestrator#generate} and passed through every LLM-calling service
 * into {@link LlmChatService}, which adds each call's usage. Because artifact generation and review
 * fan their per-artifact calls onto the {@code artifactCallExecutor} worker threads, the counters are
 * {@link AtomicLong}s so concurrent {@link #add} calls are safe without locking.
 */
public final class TokenUsageAccumulator {

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();

    /** Adds one call's prompt/completion token counts. Null counts (provider omitted them) are skipped. */
    public void add(Integer in, Integer out) {
        if (in != null) {
            inputTokens.addAndGet(in);
        }
        if (out != null) {
            outputTokens.addAndGet(out);
        }
    }

    public long inputTokens() {
        return inputTokens.get();
    }

    public long outputTokens() {
        return outputTokens.get();
    }
}
