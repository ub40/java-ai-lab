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
 * REST-API voor projecten.
 *
 * <p>Alleen HTTP: elke methode maakt een {@code AiAction} (of roept de service aan) en geeft
 * het record terug als JSON. Doordat alles via {@link AiActionService#execute} loopt,
 * gebruiken de API en een toekomstige AI-assistent (tool calling, MCP) dezelfde code.
 *
 * <ul>
 *   <li>{@code GET /api/projects[?status=...]}: alle projecten, of gefilterd op status</li>
 *   <li>{@code GET /api/projects/{id}}: één project, of 404</li>
 *   <li>{@code GET /api/projects/{id}/summary}: samenvatting van één project</li>
 *   <li>{@code GET /api/projects/summaries}: alle samenvattingen, parallel</li>
 *   <li>{@code GET /api/projects/newest}: het nieuwste project</li>
 *   <li>{@code GET /api/projects/batches?size=2}: projecten in groepjes</li>
 * </ul>
 *
 * <p>Vaste paden als {@code /summaries} gaan vóór het patroon {@code /{id}}.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final AiActionService actionService;
    private final ProjectService projectService;

    /**
     * @param actionService  voert AI-acties uit
     * @param projectService directe toegang voor endpoints die geen AI-actie zijn
     */
    public ProjectController(AiActionService actionService, ProjectService projectService) {
        this.actionService = actionService;
        this.projectService = projectService;
    }

    /**
     * @param status optioneel filter; zonder filter alle projecten
     * @return {@code {"projects": [...]}}
     */
    @GetMapping
    public ActionResult search(@RequestParam(required = false) ProjectStatus status) {
        return actionService.execute(new SearchProjects(status));
    }

    /**
     * @param id project-id uit de URL
     * @return {@code {"project": {...}}}, of 404
     */
    @GetMapping("/{id}")
    public ActionResult get(@PathVariable long id) {
        return actionService.execute(new GetProject(id));
    }

    /**
     * Duurt ongeveer 300 ms, omdat een AI-call wordt nagebootst.
     *
     * @param id project-id uit de URL
     * @return de samenvatting, of 404
     */
    @GetMapping("/{id}/summary")
    public ActionResult summary(@PathVariable long id) {
        return actionService.execute(new GenerateSummary(id));
    }

    /**
     * Alle samenvattingen parallel op virtual threads; vergelijk {@code durationMs} met 6 × 300 ms.
     *
     * @return alle samenvattingen plus de totale duur
     */
    @GetMapping("/summaries")
    public ActionResult summaries() {
        return actionService.execute(new GenerateAllSummaries());
    }

    /**
     * @return het nieuwste project (via {@code List.getLast()}, Java 21)
     */
    @GetMapping("/newest")
    public ProjectInfo newest() {
        return projectService.newest();
    }

    /**
     * Groepeert de projecten met een stream gatherer (Java 24).
     *
     * <p><b>Techniek: method validation.</b> Spring MVC controleert {@link Min @Min} en
     * {@link Max @Max} op parameters automatisch en geeft HTTP 400 bij een ongeldige waarde.
     *
     * @param size projecten per groep, 1 t/m 10 (standaard 2)
     * @return de groepjes, bijv. {@code [[p1, p2], [p3, p4], [p5, p6]]}
     */
    @GetMapping("/batches")
    public List<List<ProjectInfo>> batches(
            @RequestParam(defaultValue = "2") @Min(1) @Max(10) int size) {
        return projectService.inBatches(size);
    }
}
