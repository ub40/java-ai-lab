package com.jrevolt.javaailab.project;

import java.util.Locale;
import java.util.Objects;

/**
 * Onveranderbare gegevens van één project.
 *
 * <p><b>Techniek: record (Java 16).</b> De compiler maakt constructor, accessors,
 * {@code equals}, {@code hashCode} en {@code toString}. Alle velden zijn {@code final}, dus
 * veilig om te delen tussen (virtual) threads. Geschikt voor DTO's en API-antwoorden, niet
 * voor JPA-entities (die moeten veranderbaar zijn).
 *
 * @param id     uniek, positief
 * @param name   naam, nooit leeg
 * @param status levensfase, nooit {@code null}
 */
public record ProjectInfo(
        long id,
        String name,
        ProjectStatus status
) {

    /**
     * <b>Techniek: compact constructor (Java 16).</b> Valideert en normaliseert de waarden vóór
     * ze worden toegekend, zodat een ongeldig {@code ProjectInfo} niet kan bestaan. De naam wordt
     * ook ontdaan van spaties aan begin en eind.
     *
     * @throws IllegalArgumentException als {@code id} niet positief is of {@code name} leeg is
     * @throws NullPointerException     als {@code name} of {@code status} {@code null} is
     */
    public ProjectInfo {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive, but was " + id);
        }
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        name = name.strip();
    }

    /**
     * Maakt een project met de status als tekst, bijv. {@code "in_progress"}.
     *
     * <p><b>Techniek: flexible constructor bodies (Java 25, JEP 513).</b> Vóór Java 25 moest
     * {@code this(...)} de eerste regel zijn. Nu mag je eerst code uitvoeren (zonder {@code this}
     * te gebruiken): hier wordt de tekst eerst omgezet naar een {@link ProjectStatus}.
     *
     * @param id     uniek, positief
     * @param name   naam, nooit leeg
     * @param status statusnaam, hoofdletterongevoelig
     * @throws IllegalArgumentException als {@code status} geen geldige {@link ProjectStatus} is
     */
    public ProjectInfo(long id, String name, String status) {
        Objects.requireNonNull(status, "status must not be null");
        ProjectStatus parsed = ProjectStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        this(id, name, parsed);
    }

    /**
     * Records mogen extra methodes hebben, zolang die op de eigen velden werken.
     *
     * @return {@code true} als de status {@link ProjectStatus#IN_PROGRESS} is
     */
    public boolean active() {
        return status == ProjectStatus.IN_PROGRESS;
    }
}
