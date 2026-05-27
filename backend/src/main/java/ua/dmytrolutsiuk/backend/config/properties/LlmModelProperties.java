package ua.dmytrolutsiuk.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;

import java.util.List;

@ConfigurationProperties(prefix = "llm")
public record LlmModelProperties(List<LlmModel> models) {

    public LlmModel findById(String id) {
        return models.stream()
                .filter(m -> m.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown LLM model id: " + id));
    }
}
