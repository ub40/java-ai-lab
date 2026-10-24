package com.jrevolt.javaailab.action;

import com.jrevolt.javaailab.project.ProjectStatus;

/**
 * Actie: projecten zoeken, optioneel gefilterd op status.
 *
 * <p>Het record bevat alleen de invoer; het gedrag zit in {@link AiActionService}.
 *
 * @param status de status om op te filteren, of {@code null} voor alle projecten
 */
public record SearchProjects(
        ProjectStatus status
) implements AiAction {
}
