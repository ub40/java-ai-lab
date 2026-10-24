package com.jrevolt.javaailab.project;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Het gevraagde project bestaat niet.
 *
 * <p>De service weet niets van HTTP; {@link ResponseStatus} laat Spring MVC deze exception
 * omzetten naar <b>404 Not Found</b>, zonder {@code try/catch} in service of controller.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectNotFoundException extends RuntimeException {

    /**
     * @param id het id dat niet gevonden werd
     */
    public ProjectNotFoundException(long id) {
        super("Project " + id + " not found");
    }
}
