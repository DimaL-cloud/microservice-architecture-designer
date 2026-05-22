package ua.dmytrolutsiuk.backend.client.mermaidvalidator;

import java.util.List;

public record MermaidValidationResponse(List<DiagramResult> results) {

    public record DiagramResult(String id, boolean valid, String error) {
    }
}
