package ua.dmytrolutsiuk.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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
        return call(model, systemPrompt, userPrompt, null, responseType, null);
    }

    /**
     * Structured-output call with an explicit max-output-tokens cap (e.g. for the larger blueprint
     * response). Pass {@code null} to use the provider default.
     */
    public <T> T call(LlmModel model, String systemPrompt, String userPrompt, Integer maxOutputTokens, Class<T> responseType) {
        return call(model, systemPrompt, userPrompt, maxOutputTokens, responseType, null);
    }

    /**
     * Structured-output call that also meters token usage into {@code accumulator} (pass {@code null}
     * to skip metering, e.g. for calls made outside a project's generation run). Uses
     * {@code responseEntity(...)} so the same call yields both the parsed entity and the
     * {@link ChatResponse} carrying usage metadata.
     */
    public <T> T call(LlmModel model, String systemPrompt, String userPrompt, Integer maxOutputTokens,
                      Class<T> responseType, TokenUsageAccumulator accumulator) {
        return withRetry(() -> {
            // LenientBeanOutputConverter (not the bare Class overload) so a model that wraps its
            // JSON in a reasoning preamble or trailing note still parses instead of failing the run.
            var responseEntity = buildSpec(model, maxOutputTokens)
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .responseEntity(new LenientBeanOutputConverter<>(responseType));
            // Recorded only after a successful call returns, so a retried transient-error attempt
            // (which throws above) is never counted.
            record(accumulator, responseEntity.getResponse());
            return responseEntity.getEntity();
        });
    }

    /**
     * Raw-text call, used for Markdown/Mermaid artifacts where the whole response body is the
     * artifact. {@code maxOutputTokens} caps the output (required for large artifacts like the SDD,
     * which would otherwise truncate at the provider default).
     */
    public String callForText(LlmModel model, String systemPrompt, String userPrompt, Integer maxOutputTokens) {
        return callForText(model, systemPrompt, userPrompt, maxOutputTokens, null);
    }

    /**
     * Raw-text call that also meters token usage into {@code accumulator} (pass {@code null} to skip
     * metering). Uses {@code chatResponse()} so the {@link ChatResponse} carrying usage metadata is
     * available alongside the text body.
     */
    public String callForText(LlmModel model, String systemPrompt, String userPrompt, Integer maxOutputTokens,
                              TokenUsageAccumulator accumulator) {
        return withRetry(() -> {
            ChatResponse response = buildSpec(model, maxOutputTokens)
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            record(accumulator, response);
            return response.getResult().getOutput().getText();
        });
    }

    /**
     * Adds the response's prompt/completion token usage to {@code accumulator}. No-op when there is
     * no accumulator. A response that carries no usage metadata is logged at WARN (so a provider or
     * Spring AI regression is visible rather than silently undercounting) and otherwise ignored.
     */
    private void record(TokenUsageAccumulator accumulator, ChatResponse response) {
        if (accumulator == null) {
            return;
        }
        Usage usage = response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            log.warn("LLM response carried no token-usage metadata; tokens not counted for this call");
            return;
        }
        accumulator.add(usage.getPromptTokens(), usage.getCompletionTokens());
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
