package com.jrevolt.javaailab.action;

import com.jrevolt.javaailab.action.ActionResult.ProjectResult;
import com.jrevolt.javaailab.action.ActionResult.ProjectsResult;
import com.jrevolt.javaailab.action.ActionResult.SummariesResult;
import com.jrevolt.javaailab.action.ActionResult.SummaryResult;
import com.jrevolt.javaailab.project.ProjectInfo;
import com.jrevolt.javaailab.project.ProjectService;
import com.jrevolt.javaailab.project.ProjectStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Voert {@link AiAction}s uit: het ene toegangspunt waarlangs een controller of AI-assistent
 * de applicatie iets laat doen.
 *
 * <p><b>Techniek: constructor-injectie.</b> Spring geeft {@link ProjectService} mee via de
 * constructor, zonder {@code @Autowired}. In een unit-test: {@code new AiActionService(new ProjectService())}.
 */
@Service
public class AiActionService {

    /**
     * Duur van een nagebootste AI-call. Tijdens het wachten doet de thread niets: precies waar
     * virtual threads voor bedoeld zijn.
     */
    static final Duration SIMULATED_AI_LATENCY = Duration.ofMillis(300);

    private static final Logger log = LoggerFactory.getLogger(AiActionService.class);

    private final ProjectService projectService;

    /**
     * @param projectService levert de projectgegevens
     */
    public AiActionService(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Voert een actie uit.
     *
     * <ul>
     *   <li><b>Pattern matching voor {@code switch} (Java 21, JEP 441):</b> kiest de tak op type.
     *       Omdat {@link AiAction} sealed is, is geen {@code default} nodig.</li>
     *   <li><b>Record patterns (Java 21, JEP 440):</b> {@code case GetProject(long id)} controleert
     *       het type én haalt het veld eruit.</li>
     *   <li><b>Guard met {@code when}:</b> de eerste {@code SearchProjects}-tak geldt alleen als
     *       {@code status == null}; de specifiekere tak moet bovenaan.</li>
     * </ul>
     *
     * @param action de actie
     * @return het getypeerde resultaat
     * @throws com.jrevolt.javaailab.project.ProjectNotFoundException als het project niet bestaat
     */
    public ActionResult execute(AiAction action) {
        log.info("Executing AI action '{}': {}", describe(action), action);

        return switch (action) {
            case SearchProjects(ProjectStatus status) when status == null ->
                    new ProjectsResult(projectService.findAll());
            case SearchProjects(ProjectStatus status) ->
                    new ProjectsResult(projectService.findByStatus(status));
            case GetProject(long id) ->
                    new ProjectResult(projectService.getById(id));
            case GenerateSummary(long id) ->
                    summarize(projectService.getById(id));
            case GenerateAllSummaries() ->
                    summarizeAll();
        };
    }

    /**
     * Korte, vaste naam van een actie, bijv. voor logging of als tool-naam.
     *
     * <p><b>Techniek: unnamed patterns {@code _} (Java 22, JEP 456).</b> Alleen het type telt;
     * {@code SearchProjects(_)} betekent "wat er ook in zit", zonder ongebruikte variabele.
     *
     * @param action de actie
     * @return een naam als {@code "get-project"}
     */
    static String describe(AiAction action) {
        return switch (action) {
            case SearchProjects(_) -> "search-projects";
            case GetProject(_) -> "get-project";
            case GenerateSummary(_) -> "generate-summary";
            case GenerateAllSummaries() -> "generate-all-summaries";
        };
    }

    /**
     * Maakt de samenvatting van één project, met een nagebootste trage AI-call.
     *
     * <p>De {@code switch} over {@link ProjectStatus} is een exhaustive switch-expressie: geeft
     * een waarde terug, zonder {@code break} en zonder {@code default}.
     *
     * @param project het project
     * @return de samenvatting, met de naam van de thread
     */
    private SummaryResult summarize(ProjectInfo project) {
        simulateAiCall();

        String phase = switch (project.status()) {
            case PLANNED -> "is planned and has not started yet";
            case IN_PROGRESS -> "is currently in progress";
            case COMPLETED -> "has been completed";
        };

        return new SummaryResult(
                project.id(),
                "Project '%s' %s.".formatted(project.name(), phase),
                Thread.currentThread().toString()
        );
    }

    /**
     * Vat alle projecten parallel samen, één virtual thread per project.
     *
     * <ul>
     *   <li>{@link Executors#newVirtualThreadPerTaskExecutor()} (Java 21): een nieuwe virtual
     *       thread per taak, zonder pool.</li>
     *   <li>try-with-resources op de executor: {@code close()} wacht tot alle taken klaar zijn,
     *       daarna leest {@link Future#resultNow()} het resultaat zonder te blokkeren.</li>
     * </ul>
     *
     * <p>6 × 300 ms na elkaar is ~1,8 s; parallel ~300 ms.
     *
     * @return alle samenvattingen plus de totale duur
     */
    private SummariesResult summarizeAll() {
        List<ProjectInfo> projects = projectService.findAll();
        long start = System.nanoTime();

        List<Future<SummaryResult>> futures;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = projects.stream()
                    .map(project -> executor.submit(() -> summarize(project)))
                    .toList();
        }

        List<SummaryResult> summaries = futures.stream()
                .map(Future::resultNow)
                .toList();

        long durationMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return new SummariesResult(summaries, durationMs);
    }

    /**
     * Bootst het wachten op een taalmodel na. Op een virtual thread stapt de thread tijdens
     * {@code sleep} af van zijn carrier (unmount).
     *
     * @throws IllegalStateException bij een interrupt; de interrupt-vlag wordt eerst hersteld
     */
    private static void simulateAiCall() {
        try {
            Thread.sleep(SIMULATED_AI_LATENCY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the AI model", e);
        }
    }
}
