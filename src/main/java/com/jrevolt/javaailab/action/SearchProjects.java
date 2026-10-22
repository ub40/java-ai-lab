package com.jrevolt.javaailab.action;

import com.jrevolt.javaailab.project.ProjectStatus;

/**
 * Action: find projects, optionally filtered by status.
 *
 * <p>A record that implements a sealed interface must be {@code final}; records already are,
 * so nothing extra is needed. The record holds only the <em>input</em> of the action; the
 * behaviour lives in {@link AiActionService}.
 *
 * @param status the status to filter on, or {@code null} to return all projects
 */
public record SearchProjects(
        ProjectStatus status
) implements AiAction {
}
