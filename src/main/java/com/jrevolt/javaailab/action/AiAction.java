package com.jrevolt.javaailab.action;

/**
 * An action that an AI assistant can ask the application to perform.
 *
 * <p><b>Technique: sealed interface (Java 17, JEP 409).</b> A normal interface can be
 * implemented by any class, anywhere. A {@code sealed} interface lists exactly which types
 * may implement it ({@code permits ...}); any other implementation is a compile error.
 *
 * <p><b>Why that matters:</b> because the compiler knows <em>all</em> possible actions, a
 * {@code switch} over an {@code AiAction} can be checked for completeness. There is no need
 * for a {@code default} branch, and if a new action is added to this list, every switch that
 * does not handle it yet stops compiling. A forgotten case becomes a compile error instead of
 * a bug in production.
 *
 * <p>Combined with records this is called an <em>algebraic data type</em>: a fixed set of
 * variants, each carrying its own data. In this project every variant is a possible
 * "tool" for an AI model.
 *
 * @see AiActionService#execute(AiAction)
 */
public sealed interface AiAction
        permits SearchProjects, GetProject, GenerateSummary, GenerateAllSummaries {
}
