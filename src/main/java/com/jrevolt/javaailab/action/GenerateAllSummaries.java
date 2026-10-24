package com.jrevolt.javaailab.action;

/**
 * Actie: alle projecten samenvatten, parallel op virtual threads.
 *
 * <p>Een record zonder velden: de actie zelf is de boodschap. Probeer een actie aan
 * {@code permits} toe te voegen: de compiler wijst dan elke onvolledige {@code switch} aan.
 */
public record GenerateAllSummaries() implements AiAction {
}
