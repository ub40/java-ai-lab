package com.jrevolt.javaailab.action;

/**
 * Action: generate a summary for every project, in parallel on virtual threads.
 *
 * <p>This record has no components, so it carries no data: the action itself is the whole
 * message. It is still useful as a record, because it takes part in the sealed
 * {@link AiAction} hierarchy and can be matched with the record pattern
 * {@code GenerateAllSummaries()}.
 *
 * <p><b>Try it:</b> remove this type from the {@code permits} list of {@link AiAction}, or
 * add a new action there, and see how the compiler immediately reports every {@code switch}
 * that is no longer complete.
 */
public record GenerateAllSummaries() implements AiAction {
}
