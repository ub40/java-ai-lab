package com.jrevolt.javaailab.action;

/**
 * Actie: een korte samenvatting van één project maken (AI-call nagebootst met
 * {@link AiActionService#SIMULATED_AI_LATENCY}).
 *
 * @param projectId id van het project
 */
public record GenerateSummary(
        long projectId
) implements AiAction {
}
