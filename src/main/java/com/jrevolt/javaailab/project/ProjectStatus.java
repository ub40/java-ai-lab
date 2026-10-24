package com.jrevolt.javaailab.project;

/**
 * Levensfase van een project.
 *
 * <p>Een enum maakt ongeldige waarden onmogelijk, en een {@code switch} erover kan
 * <em>exhaustive</em> zijn: bij een nieuwe constante wijst de compiler elke switch aan die hem
 * nog niet afhandelt. Spring zet {@code ?status=IN_PROGRESS} automatisch om; een onbekende
 * waarde geeft HTTP 400.
 */
public enum ProjectStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED
}
