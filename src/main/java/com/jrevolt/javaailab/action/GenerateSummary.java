package com.jrevolt.javaailab.action;

/**
 * Action: generate a short text summary of one project.
 *
 * <p>In a real AI application this would call a language model. In this lab the call is
 * simulated with a fixed delay (see {@link AiActionService#SIMULATED_AI_LATENCY}), which
 * behaves like any slow I/O call.
 *
 * @param projectId id of the project to summarize
 */
public record GenerateSummary(
        long projectId
) implements AiAction {
}
