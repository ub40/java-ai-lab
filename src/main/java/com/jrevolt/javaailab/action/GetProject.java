package com.jrevolt.javaailab.action;

/**
 * Action: load a single project by its id.
 *
 * @param projectId id of the project to load
 */
public record GetProject(
        long projectId
) implements AiAction {
}
