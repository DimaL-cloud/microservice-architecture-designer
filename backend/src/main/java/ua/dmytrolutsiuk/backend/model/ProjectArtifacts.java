package ua.dmytrolutsiuk.backend.model;

import java.util.List;

/**
 * The set of artifacts generated for a project. Persisted as jsonb and sent to the frontend.
 * Diagram fields hold Mermaid source; {@link #sdd} and {@link Adr#markdown} hold Markdown.
 */
public record ProjectArtifacts(
        String c4Context,
        String c4Container,
        String sdd,
        List<Adr> adrs,
        List<SequenceDiagram> sequenceDiagrams
) {

    public record Adr(String id, String title, String markdown) {
    }

    public record SequenceDiagram(String id, String title, String code) {
    }
}
