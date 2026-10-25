package com.jrevolt.javaailab.tools;

import com.jrevolt.javaailab.action.ActionResult;
import com.jrevolt.javaailab.action.AiActionService;
import com.jrevolt.javaailab.action.GetProject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tools die het taalmodel mag aanroepen.
 *
 * <p>Het model ziet alleen de methodenaam, de {@code description} en de parameters, nooit de
 * code. Elke tool roept {@link AiActionService} aan, dus de REST-API en het model gebruiken
 * dezelfde logica.
 */

@Component
public class ProjectTools {

    private final AiActionService actionService;
    private static final Logger log = LoggerFactory.getLogger(ProjectTools.class);

    public ProjectTools(AiActionService actionService) {
        this.actionService = actionService;
    }

    @Tool(description = "Get one software project by its numeric id. Returns its name and status.")
    public ActionResult getProject(@ToolParam(description = "The project id, for example 1") long projectId) {
        log.info("Model called tool getProject(projectId={})", projectId);
        return actionService.execute(new GetProject(projectId));
    }
}
