package com.jrevolt.javaailab.project;

import com.jrevolt.javaailab.action.ActionResult;
import com.jrevolt.javaailab.action.AiActionService;
import com.jrevolt.javaailab.action.GenerateAllSummaries;
import com.jrevolt.javaailab.action.GenerateSummary;
import com.jrevolt.javaailab.action.GetProject;
import com.jrevolt.javaailab.action.SearchProjects;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for projects.
 *
 * <p><b>Responsibility:</b> only HTTP. Each method translates a request into an
 * {@code AiAction} (or a direct service call) and returns the result; Spring turns the
 * returned record into JSON. There is no business logic here; that lives in
 * {@link AiActionService} and {@link ProjectService}.
 *
 * <p>Routing every request through {@link AiActionService#execute} means the HTTP API and a
 * future AI assistant (tool calling, MCP) use exactly the same code path.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/projects}: all projects</li>
 *   <li>{@code GET /api/projects?status=IN_PROGRESS}: projects with that status</li>
 *   <li>{@code GET /api/projects/{id}}: one project, or 404</li>
 *   <li>{@code GET /api/projects/{id}/summary}: summary of one project</li>
 *   <li>{@code GET /api/projects/summaries}: summaries of all projects, in parallel</li>
 *   <li>{@code GET /api/projects/newest}: the most recently added project</li>
 *   <li>{@code GET /api/projects/batches?size=2}: projects in groups of {@code size}</li>
 * </ul>
 *
 * <p>Literal paths such as {@code /summaries} and {@code /newest} take precedence over the
 * pattern {@code /{id}}, so they do not clash.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final AiActionService actionService;
    private final ProjectService projectService;

    /**
     * Constructor injection: Spring passes in both service beans.
     *
     * @param actionService  executes AI actions
     * @param projectService direct access for endpoints that are not AI actions
     */
    public ProjectController(AiActionService actionService, ProjectService projectService) {
        this.actionService = actionService;
        this.projectService = projectService;
    }

    /**
     * @param status optional filter; when omitted, all projects are returned
     * @return the matching projects as {@code {"projects": [...]}}
     */
    @GetMapping
    public ActionResult search(@RequestParam(required = false) ProjectStatus status) {
        return actionService.execute(new SearchProjects(status));
    }

    /**
     * @param id the project id from the URL
     * @return the project as {@code {"project": {...}}}, or HTTP 404 if it does not exist
     */
    @GetMapping("/{id}")
    public ActionResult get(@PathVariable long id) {
        return actionService.execute(new GetProject(id));
    }

    /**
     * Takes about {@code 300 ms}, because it simulates a call to an AI model.
     *
     * @param id the project id from the URL
     * @return the summary, or HTTP 404 if the project does not exist
     */
    @GetMapping("/{id}/summary")
    public ActionResult summary(@PathVariable long id) {
        return actionService.execute(new GenerateSummary(id));
    }

    /**
     * Summarizes all projects in parallel on virtual threads. Compare {@code durationMs} in
     * the response with 6 × 300 ms.
     *
     * @return all summaries plus the total duration
     */
    @GetMapping("/summaries")
    public ActionResult summaries() {
        return actionService.execute(new GenerateAllSummaries());
    }

    /**
     * @return the most recently added project (uses {@code List.getLast()}, Java 21)
     */
    @GetMapping("/newest")
    public ProjectInfo newest() {
        return projectService.newest();
    }

    /**
     * Groups the projects, using a stream gatherer (Java 24).
     *
     * <p><b>Technique: method validation.</b> {@link Min @Min} and {@link Max @Max} come from
     * Jakarta Bean Validation (the {@code spring-boot-starter-validation} dependency). Since
     * Spring Framework 6.1, Spring MVC validates such constraints on controller parameters
     * automatically and answers HTTP <b>400 Bad Request</b> when they are violated, before
     * this method is even called.
     *
     * @param size projects per group, between 1 and 10 (default 2)
     * @return a list of groups, for example {@code [[p1, p2], [p3, p4], [p5, p6]]}
     */
    @GetMapping("/batches")
    public List<List<ProjectInfo>> batches(
            @RequestParam(defaultValue = "2") @Min(1) @Max(10) int size) {
        return projectService.inBatches(size);
    }
}
