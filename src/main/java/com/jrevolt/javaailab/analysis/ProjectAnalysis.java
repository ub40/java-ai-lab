package com.jrevolt.javaailab.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Risicobeoordeling van een project door het taalmodel.
 *
 * <p>Spring AI maakt uit dit record een JSON-schema voor het model, en zet het JSON-antwoord
 * weer om naar dit record. De validatie-annotaties doen niets vanzelf: de {@code Validator} in
 * {@link ProjectAnalysisService} controleert ze, omdat AI-output nooit blind vertrouwd mag worden.
 *
 * @param risk              leveringsrisico, nooit {@code null}
 * @param summary           korte beoordeling, niet leeg, maximaal 300 tekens
 * @param recommendedAction volgende stap voor het team, niet leeg
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html">Spring AI: Structured Output Converter</a>
 */
public record ProjectAnalysis(
        @NotNull Risk risk,
        @NotBlank @Size(max = 300) String summary,
        @NotBlank String recommendedAction
) {
}
