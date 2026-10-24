package com.jrevolt.javaailab.analysis;

/**
 * Leveringsrisico van een project.
 *
 * <p>Een enum in plaats van een {@code String} beperkt het model tot precies deze drie waarden.
 * Spring AI zet ze in het JSON-schema; een andere waarde (bijv. {@code "HOOG"}) faalt direct.
 */
public enum Risk {
    LOW,
    MEDIUM,
    HIGH
}
