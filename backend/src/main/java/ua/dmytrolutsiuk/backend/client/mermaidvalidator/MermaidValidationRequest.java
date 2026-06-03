package ua.dmytrolutsiuk.backend.client.mermaidvalidator;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record MermaidValidationRequest(List<Diagram> diagrams) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Diagram(String id, String code) {
    }
}
