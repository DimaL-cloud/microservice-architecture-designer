package ua.dmytrolutsiuk.backend.service;

import ua.dmytrolutsiuk.backend.model.ArchitectureBlueprint;

import java.util.List;

/**
 * Enforces the hard upper bounds on a blueprint's decision and key-flow counts so downstream fan-out
 * (one ADR per decision, one sequence diagram per flow) cannot grow unboundedly. Applied after every
 * call that produces an {@link ArchitectureBlueprint} (generation and review), so the cap lives in
 * exactly one place.
 */
final class BlueprintClamp {

    private BlueprintClamp() {
    }

    /**
     * Returns a blueprint whose {@code decisions} and {@code keyFlows} are truncated to at most
     * {@code maxAdrs} / {@code maxFlows} respectively, preserving order (most-impactful first). The
     * same instance is returned unchanged when both lists are already within bounds.
     */
    static ArchitectureBlueprint clamp(ArchitectureBlueprint blueprint, int maxAdrs, int maxFlows) {
        List<ArchitectureBlueprint.Decision> decisions = limit(blueprint.decisions(), maxAdrs);
        List<ArchitectureBlueprint.Flow> flows = limit(blueprint.keyFlows(), maxFlows);
        if (decisions == blueprint.decisions() && flows == blueprint.keyFlows()) {
            return blueprint;
        }
        return new ArchitectureBlueprint(
                blueprint.systemName(),
                blueprint.systemOverview(),
                blueprint.actors(),
                blueprint.containers(),
                blueprint.relationships(),
                decisions,
                flows
        );
    }

    private static <T> List<T> limit(List<T> values, int max) {
        if (values == null || values.size() <= max) {
            return values;
        }
        return List.copyOf(values.subList(0, max));
    }
}
