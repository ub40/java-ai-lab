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
 * Executes {@link AiAction}s: the single entry point through which an AI assistant (or a
 * controller) asks the application to do something.
 *
 * <p><b>Technique: constructor injection.</b> The {@link ProjectService} dependency is a
 * constructor parameter and stored in a {@code final} field. Spring sees the single
 * constructor and passes in the bean automatically; no {@code @Autowired} is needed. This is
 * the recommended style: the dependency can never be {@code null} or change, and in a unit
 * test you simply call {@code new AiActionService(new ProjectService())}.
 */
@Service
public class AiActionService {

    /**
     * How long a simulated AI call takes. A real language model often needs hundreds of
     * milliseconds or more to answer, during which the thread is only <em>waiting</em>. That
     * is exactly the situation where virtual threads shine.
     */
    static final Duration SIMULATED_AI_LATENCY = Duration.ofMillis(300);

    private static final Logger log = LoggerFactory.getLogger(AiActionService.class);

    private final ProjectService projectService;

    /**
     * @param projectService provides the project data, injected by Spring
     */
    public AiActionService(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Executes an action and returns its result.
     *
     * <p><b>Technique: pattern matching for {@code switch} (Java 21, JEP 441).</b> The
     * {@code switch} checks the <em>type</em> of {@code action} and picks the matching branch.
     * Because {@link AiAction} is sealed, the compiler knows every possible type, so the
     * switch is <em>exhaustive</em>: no {@code default} branch is needed, and a newly added
     * action that is not handled here is a compile error.
     *
     * <p><b>Technique: record patterns (Java 21, JEP 440).</b> {@code case GetProject(long id)}
     * does two things at once: it checks that the action is a {@code GetProject} <em>and</em>
     * unpacks its component into the variable {@code id}. Compare with the older style
     * {@code case GetProject get -> get.projectId()}.
     *
     * <p><b>Technique: guarded patterns with {@code when}.</b> The first {@code SearchProjects}
     * branch only matches when the extra condition is true. Branches are tried from top to
     * bottom, so the more specific (guarded) branch must come first.
     *
     * @param action the action to execute
     * @return the typed result of that action
     * @throws com.jrevolt.javaailab.project.ProjectNotFoundException if the action refers to a
     *                                                                project that does not exist
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
     * Returns a short, stable name for an action, for example for logging or as a tool name.
     *
     * <p><b>Technique: unnamed patterns {@code _} (Java 22, JEP 456).</b> Here only the
     * <em>type</em> of the action matters, not its data. Writing {@code SearchProjects(_)}
     * says "a SearchProjects, whatever is inside" without inventing a variable name that is
     * never used. This makes the intent clear and avoids "unused variable" warnings.
     *
     * @param action the action to describe
     * @return a kebab-case name such as {@code "get-project"}
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
     * Generates the summary for one project, simulating a slow AI call.
     *
     * <p>The {@code switch} over {@link ProjectStatus} is an exhaustive switch
     * <em>expression</em>: it returns a value, uses {@code ->} (no fall-through, no
     * {@code break}) and covers all enum constants, so no {@code default} is needed.
     *
     * @param project the project to summarize
     * @return the summary, including the name of the thread that produced it
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
     * Generates summaries for all projects in parallel, one virtual thread per project.
     *
     * <p><b>Technique: virtual-thread-per-task executor (Java 21, JEP 444).</b>
     * {@link Executors#newVirtualThreadPerTaskExecutor()} starts a <em>new</em> virtual
     * thread for every submitted task. There is no pool: virtual threads are so cheap that
     * pooling them is an anti-pattern.
     *
     * <p><b>Technique: try-with-resources on an {@code ExecutorService} (Java 19+).</b>
     * {@code ExecutorService} is {@link AutoCloseable}. Closing it at the end of the
     * {@code try} block <em>waits until all tasks have finished</em>. After the block, every
     * {@link Future} is guaranteed to be done, so {@link Future#resultNow()} can read the
     * result without blocking or a checked exception.
     *
     * <p>With 6 projects of 300 ms each, doing them one after another would take about
     * 1.8 seconds. In parallel it takes about 300 ms: the same "waiting is cheap" effect that
     * was measured with {@code hey} in topic 1.
     *
     * @return all summaries plus the total duration
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
     * Simulates waiting for a language model.
     *
     * <p>On a virtual thread, {@code sleep} <em>unmounts</em> the virtual thread from its
     * carrier, so the carrier can run other work in the meantime.
     *
     * @throws IllegalStateException if the thread is interrupted while waiting; the interrupt
     *                               flag is restored first so callers can still see it
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
