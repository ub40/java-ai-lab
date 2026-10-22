package com.jrevolt.javaailab.project;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a project with the requested id does not exist.
 *
 * <p><b>Why a custom exception?</b> The service layer should not know about HTTP. It only
 * says "this project does not exist". The {@link ResponseStatus} annotation tells Spring MVC
 * to translate this exception into HTTP <b>404 Not Found</b> when it reaches a controller,
 * so neither the service nor the controller needs any {@code try/catch}.
 *
 * <p>It extends {@link RuntimeException} (unchecked), so callers are not forced to declare
 * or catch it, which is the usual style in Spring applications.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectNotFoundException extends RuntimeException {

    /**
     * @param id the id that was looked up but not found
     */
    public ProjectNotFoundException(long id) {
        super("Project " + id + " not found");
    }
}
