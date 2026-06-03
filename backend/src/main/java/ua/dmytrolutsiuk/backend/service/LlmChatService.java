package ua.dmytrolutsiuk.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.LlmProvider;

import java.util.function.Supplier;

@Service
@Slf4j
public class LlmChatService {

    private static final int MAX_RETRIES = 6;
    private static final long INITIAL_BACKOFF_MS = 5_000;
    private static final long MAX_BACKOFF_MS = 60_000;

    private final ChatClient anthropicChatClient;
    private final ChatClient openAiChatClient;

    public LlmChatService(
            @Qualifier("anthropicChatClient") ChatClient anthropicChatClient,
            @Qualifier("openAiChatClient") ChatClient openAiChatClient
    ) {
        this.anthropicChatClient = anthropicChatClient;
        this.openAiChatClient = openAiChatClient;
    }

    public <T> T call(LlmModel model, String systemPrompt, String userPrompt, Class<T> responseType) {
        return call(model, systemPrompt, userPrompt, null, responseType);
    }

    /**
     * Structured-output call with an explicit max-output-tokens cap (e.g. for the larger blueprint
     * response). Pass {@code null} to use the provider default.
     */
    public <T> T call(LlmModel model, String systemPrompt, String userPrompt, Integer maxOutputTokens, Class<T> responseType) {
        return withRetry(() -> buildSpec(model, maxOutputTokens)
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(responseType));
    }

    /**
     * Raw-text call, used for Markdown/Mermaid artifacts where the whole response body is the
     * artifact. {@code maxOutputTokens} caps the output (required for large artifacts like the SDD,
     * which would otherwise truncate at the provider default).
     */
    public String callForText(LlmModel model, String systemPrompt, String userPrompt, Integer maxOutputTokens) {
        return withRetry(() -> buildSpec(model, maxOutputTokens)
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content());
    }

    /**
     * Retries a call on transient provider errors — rate limits (HTTP 429) and overload (HTTP 529) —
     * with exponential backoff. These are common on lower API tiers and would otherwise fail the
     * whole project. Non-transient errors propagate immediately.
     */
    private <T> T withRetry(Supplier<T> action) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException e) {
                if (attempt >= MAX_RETRIES || !isTransient(e)) {
                    throw e;
                }
                log.warn("Transient LLM error (attempt {}/{}), retrying in {}ms: {}",
                        attempt, MAX_RETRIES, backoffMs, e.getMessage());
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
    }

    private static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName().toLowerCase();
            String message = t.getMessage() == null ? "" : t.getMessage().toLowerCase();
            if (name.contains("ratelimit") || name.contains("overload")
                    || message.contains("429") || message.contains("529")
                    || message.contains("rate limit") || message.contains("rate_limit")
                    || message.contains("overloaded")) {
                return true;
            }
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off from a transient LLM error", e);
        }
    }

    private ChatClientRequestSpec buildSpec(LlmModel model, Integer maxOutputTokens) {
        ChatClient client = clientFor(model.provider());
        return switch (model.provider()) {
            case ANTHROPIC -> {
                AnthropicChatOptions.Builder options = AnthropicChatOptions.builder().model(model.id());
                if (maxOutputTokens != null) {
                    options.maxTokens(maxOutputTokens);
                }
                yield client.prompt().options(options);
            }
            // OpenAI reasoning models (gpt-5, ...) require maxCompletionTokens, not maxTokens.
            case OPENAI -> {
                OpenAiChatOptions.Builder options = OpenAiChatOptions.builder().model(model.id());
                if (maxOutputTokens != null) {
                    options.maxCompletionTokens(maxOutputTokens);
                }
                yield client.prompt().options(options);
            }
        };
    }

    private ChatClient clientFor(LlmProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> anthropicChatClient;
            case OPENAI -> openAiChatClient;
        };
    }
}
