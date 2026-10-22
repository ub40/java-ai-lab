package com.jrevolt.javaailab.project;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable information about one project.
 *
 * <p><b>Technique: record (Java 16).</b> A record is a class whose only job is to carry data.
 * The compiler generates the constructor, the accessors ({@code id()}, {@code name()},
 * {@code status()}), {@code equals}, {@code hashCode} and {@code toString}. All fields are
 * {@code final}, so a {@code ProjectInfo} can never change after it is created. That makes it
 * safe to share between threads, which matters when many virtual threads read it at once.
 * Spring (Jackson) turns a record into JSON automatically.
 *
 * <p>Records are a good fit for DTOs, API responses, events and value objects. They are
 * <em>not</em> a good fit for JPA entities, because JPA needs mutable objects with a
 * no-argument constructor.
 *
 * @param id     unique, positive identifier
 * @param name   human-readable name, never blank
 * @param status lifecycle phase, never {@code null}
 */
public record ProjectInfo(
        long id,
        String name,
        ProjectStatus status
) {

    /**
     * <b>Technique: compact constructor (Java 16).</b>
     *
     * <p>A compact constructor has no parameter list: it runs <em>before</em> the fields are
     * assigned and can validate or normalise the incoming values. This guarantees that an
     * invalid {@code ProjectInfo} can never exist ("make illegal states unrepresentable").
     * Here the name is also stripped of surrounding whitespace by reassigning the parameter.
     *
     * @throws IllegalArgumentException if {@code id} is not positive or {@code name} is blank
     * @throws NullPointerException     if {@code name} or {@code status} is {@code null}
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
     * Creates a project from a status given as text, for example {@code "in_progress"}.
     *
     * <p><b>Technique: flexible constructor bodies (Java 25, JEP 513).</b>
     *
     * <p>Before Java 25, the call to {@code this(...)} or {@code super(...)} had to be the
     * <em>very first</em> statement of a constructor. To convert or check an argument first,
     * you needed a separate static helper method. Since Java 25 you may run statements
     * <em>before</em> {@code this(...)}, as long as they do not touch {@code this} (the object
     * does not exist yet). Here the text is checked and converted to a {@link ProjectStatus}
     * first, and only then is the canonical constructor called.
     *
     * @param id     unique, positive identifier
     * @param name   human-readable name, never blank
     * @param status status name, case-insensitive (e.g. {@code "planned"}, {@code "COMPLETED"})
     * @throws IllegalArgumentException if {@code status} is not a valid {@link ProjectStatus}
     */
    public ProjectInfo(long id, String name, String status) {
        Objects.requireNonNull(status, "status must not be null");
        ProjectStatus parsed = ProjectStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        this(id, name, parsed);
    }

    /**
     * Whether work on this project is currently going on.
     *
     * <p>Records may contain extra methods, as long as they derive their result from the
     * record's own fields. This keeps small pieces of logic next to the data they belong to.
     *
     * @return {@code true} if the status is {@link ProjectStatus#IN_PROGRESS}
     */
    public boolean active() {
        return status == ProjectStatus.IN_PROGRESS;
    }
}
