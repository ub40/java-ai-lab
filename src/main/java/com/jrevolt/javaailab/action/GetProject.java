package com.jrevolt.javaailab.action;

/**
 * Actie: één project ophalen.
 *
 * @param projectId id van het project
 */
public record GetProject(
        long projectId
) implements AiAction {
}
