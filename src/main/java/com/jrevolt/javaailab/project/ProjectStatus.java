package com.jrevolt.javaailab.project;

/**
 * The lifecycle phase of a project.
 *
 * <p><b>Why an enum?</b> The set of possible statuses is fixed and known at compile time.
 * An enum makes invalid values impossible (no typos like {@code "IN_PROGESS"}), and a
 * {@code switch} over an enum can be <em>exhaustive</em>: if a new constant is added,
 * the compiler points at every switch that does not handle it yet
 * (see {@code AiActionService#summarize}).
 *
 * <p>Spring converts a request parameter such as {@code ?status=IN_PROGRESS} into this
 * enum automatically. An unknown value results in HTTP 400 Bad Request.
 */
public enum ProjectStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED
}
