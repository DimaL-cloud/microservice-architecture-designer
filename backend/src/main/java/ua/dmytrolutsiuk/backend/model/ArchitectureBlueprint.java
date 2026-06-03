package ua.dmytrolutsiuk.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Canonical architecture model produced by the first ("blueprint") LLM call. Every subsequent
 * per-artifact call is grounded on this same object so the diagrams, SDD, ADRs and sequence
 * diagrams stay mutually consistent (same system name, containers, relationships, decisions, flows).
 * It also enumerates exactly how many ADRs ({@link #decisions}) and sequence diagrams
 * ({@link #keyFlows}) to produce.
 *
 * <p>Returned as Spring AI structured output, so required fields are marked for the schema.
 */
public record ArchitectureBlueprint(
        @JsonProperty(required = true) String systemName,
        @JsonProperty(required = true) String systemOverview,
        @JsonProperty(required = true) List<Actor> actors,
        @JsonProperty(required = true) List<Container> containers,
        @JsonProperty(required = true) List<Relationship> relationships,
        @JsonProperty(required = true) List<Decision> decisions,
        @JsonProperty(required = true) List<Flow> keyFlows
) {

    /** Something outside the system boundary: a person or an external system. */
    public record Actor(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String type,
            String description
    ) {
    }

    /** Something inside the system boundary: a service, datastore, queue, cache, gateway, etc. */
    public record Container(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String kind,
            String technology,
            String responsibility
    ) {
    }

    /** A directed dependency between two actors/containers, referenced by id. */
    public record Relationship(
            @JsonProperty(required = true) String fromId,
            @JsonProperty(required = true) String toId,
            @JsonProperty(required = true) String label,
            String protocol
    ) {
    }

    /** An architecture decision; one ADR is generated per decision. */
    public record Decision(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true) String title,
            String context,
            String decision,
            String alternatives,
            String consequences
    ) {
    }

    /** A key end-to-end use case; one sequence diagram is generated per flow. */
    public record Flow(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true) String title,
            List<String> participantIds,
            List<String> steps
    ) {
    }
}
