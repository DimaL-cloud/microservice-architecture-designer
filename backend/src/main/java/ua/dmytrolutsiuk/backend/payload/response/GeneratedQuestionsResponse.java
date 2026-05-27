package ua.dmytrolutsiuk.backend.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import ua.dmytrolutsiuk.backend.model.GeneratedQuestionType;

import java.util.List;

public record GeneratedQuestionsResponse(
        @JsonProperty(required = true) List<GeneratedQuestion> questions
) {

    public record GeneratedQuestion(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true) String label,
            String helpText,
            @JsonProperty(required = true) GeneratedQuestionType type,
            List<String> options
    ) {
    }
}
