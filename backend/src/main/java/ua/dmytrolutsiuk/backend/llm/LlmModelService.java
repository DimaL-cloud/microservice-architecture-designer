package ua.dmytrolutsiuk.backend.llm;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmModelService {

    private static final List<LlmModel> MODELS = List.of(
            new LlmModel(
                    "claude-opus-4-7",
                    LlmProvider.ANTHROPIC,
                    "Claude Opus 4.7",
                    "Anthropic's most capable model. Best for complex architecture reasoning and high-quality design output."
            ),
            new LlmModel(
                    "claude-sonnet-4-6",
                    LlmProvider.ANTHROPIC,
                    "Claude Sonnet 4.6",
                    "Balanced Anthropic model with strong reasoning and faster, cheaper responses than Opus."
            ),
            new LlmModel(
                    "claude-haiku-4-5",
                    LlmProvider.ANTHROPIC,
                    "Claude Haiku 4.5",
                    "Fast and cost-effective Anthropic model for quick iterations and lightweight tasks."
            ),
            new LlmModel(
                    "gpt-5",
                    LlmProvider.OPENAI,
                    "GPT-5",
                    "OpenAI's flagship model with strong general-purpose reasoning and broad capabilities."
            ),
            new LlmModel(
                    "gpt-5-mini",
                    LlmProvider.OPENAI,
                    "GPT-5 mini",
                    "Smaller, faster, cheaper variant of GPT-5 for lower-cost or higher-throughput workloads."
            )
    );

    public List<LlmModel> getAvailableModels() {
        return MODELS;
    }
}
