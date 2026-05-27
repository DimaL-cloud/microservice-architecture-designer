package ua.dmytrolutsiuk.backend.service;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ua.dmytrolutsiuk.backend.model.LlmModel;
import ua.dmytrolutsiuk.backend.model.LlmProvider;

@Service
public class LlmChatService {

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
        ChatClient client = clientFor(model.provider());
        ChatClientRequestSpec spec = switch (model.provider()) {
            case ANTHROPIC -> client.prompt().options(AnthropicChatOptions.builder().model(model.id()));
            case OPENAI -> client.prompt().options(OpenAiChatOptions.builder().model(model.id()));
        };
        return spec.system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(responseType);
    }

    private ChatClient clientFor(LlmProvider provider) {
        return switch (provider) {
            case ANTHROPIC -> anthropicChatClient;
            case OPENAI -> openAiChatClient;
        };
    }
}
