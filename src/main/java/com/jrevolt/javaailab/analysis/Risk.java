package com.jrevolt.javaailab.analysis;

/**
 * Delivery risk of a project, as assessed by the language model.
 *
 * <p><b>Why an enum instead of a {@code String}?</b> It limits the model to exactly three
 * values. Spring AI lists them in the JSON schema it sends to the model, and if the model
 * still answers something else (for example {@code "medium-ish"} or {@code "HOOG"}), the
 * conversion fails immediately instead of letting an unknown value into the application.
 */
public enum Risk {
    LOW,
    MEDIUM,
    HIGH
}
