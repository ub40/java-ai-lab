package com.jrevolt.javaailab.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A language model's risk assessment of a project, as a typed Java object.
 *
 * <p><b>Technique: record as structured-output target.</b> Spring AI generates a JSON schema
 * from the components of this record (names and types) and asks the model to answer in that
 * shape. The JSON answer is then converted into this record with Jackson.
 *
 * <p><b>Technique: Bean Validation on record components.</b> The annotations do nothing by
 * themselves; they describe the rules. A {@code jakarta.validation.Validator} checks them
 * (step 3 of topic 4.4). This is needed because model output must never be trusted blindly:
 * a model can leave a field empty or write far too much text.
 *
 * @param risk              delivery risk, one of {@link Risk}; never {@code null}
 * @param summary           short assessment, not blank, at most 300 characters
 * @param recommendedAction what the team should do next, not blank
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html">Spring AI reference: Structured Output Converter</a>
 */
public record ProjectAnalysis(
        @NotNull Risk risk,
        @NotBlank @Size(max = 300) String summary,
        @NotBlank String recommendedAction
) {
}
