package com.jrevolt.javaailab.action;

/**
 * Een actie die een AI-assistent door de applicatie kan laten uitvoeren.
 *
 * <p><b>Techniek: sealed interface (Java 17, JEP 409).</b> Alleen de types in {@code permits}
 * mogen deze interface implementeren. Daardoor kent de compiler alle acties, is een
 * {@code switch} erover controleerbaar op volledigheid, en wordt een vergeten geval een
 * compileerfout in plaats van een bug. Elke variant is een mogelijke "tool" voor een model.
 *
 * @see AiActionService#execute(AiAction)
 */
public sealed interface AiAction
        permits SearchProjects, GetProject, GenerateSummary, GenerateAllSummaries {
}
