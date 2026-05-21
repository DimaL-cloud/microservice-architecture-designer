package ua.dmytrolutsiuk.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import ua.dmytrolutsiuk.backend.model.LlmModel;

import java.util.List;

@ConfigurationProperties(prefix = "llm")
public record LlmModelProperties(List<LlmModel> models) {
}
