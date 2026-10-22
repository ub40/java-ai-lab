package com.jrevolt.javaailab.project;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Gatherers;

/**
 * Business logic and data access for projects.
 *
 * <p><b>Why a service?</b> A controller should only deal with HTTP (URLs, parameters,
 * status codes). Everything else (finding, filtering, rules) lives in a service. This keeps
 * the logic reusable (a controller, a scheduled job or an AI tool can all call it) and
 * easy to unit test without starting a web server.
 *
 * <p>{@link Service @Service} makes this class a Spring bean: Spring creates exactly one
 * instance and injects it wherever it is needed (see the constructor of
 * {@code AiActionService}).
 *
 * <p>For this lab the data is an in-memory list instead of a real database. Because the list
 * is immutable ({@link List#of}) and {@link ProjectInfo} is a record, this class is
 * thread-safe without any locking.
 */
@Service
public class ProjectService {

    /**
     * The "database". {@link List#of} returns an unmodifiable list: any attempt to add or
     * remove an element throws an exception. The order is the order in which projects were
     * added, so the last element is the newest project.
     */
    private final List<ProjectInfo> projects = List.of(
            new ProjectInfo(1, "Java AI Lab", ProjectStatus.IN_PROGRESS),
            new ProjectInfo(2, "Virtual Threads Benchmark", ProjectStatus.COMPLETED),
            new ProjectInfo(3, "Spring AI with Ollama", "planned"),
            new ProjectInfo(4, "MCP Server", "planned"),
            new ProjectInfo(5, "Resilience Patterns", ProjectStatus.IN_PROGRESS),
            new ProjectInfo(6, "OpenAPI Documentation", "planned")
    );

    /**
     * @return all projects, in the order they were added
     */
    public List<ProjectInfo> findAll() {
        return projects;
    }

    /**
     * Looks up a project by id.
     *
     * <p><b>Why {@link Optional}?</b> It makes "maybe there is no result" visible in the
     * method signature, so the caller is forced to think about the missing case instead of
     * getting a surprise {@code null}.
     *
     * @param id the project id
     * @return the project, or an empty {@code Optional} if it does not exist
     */
    public Optional<ProjectInfo> findById(long id) {
        return projects.stream()
                .filter(project -> project.id() == id)
                .findFirst();
    }

    /**
     * Same as {@link #findById(long)}, but treats a missing project as an error.
     *
     * @param id the project id
     * @return the project
     * @throws ProjectNotFoundException if no project has this id (becomes HTTP 404)
     */
    public ProjectInfo getById(long id) {
        return findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    /**
     * @param status the status to filter on
     * @return all projects with that status, possibly an empty list
     */
    public List<ProjectInfo> findByStatus(ProjectStatus status) {
        return projects.stream()
                .filter(project -> project.status() == status)
                .toList();
    }

    /**
     * Returns the most recently added project.
     *
     * <p><b>Technique: sequenced collections (Java 21, JEP 431).</b> Before Java 21 you had to
     * write {@code list.get(list.size() - 1)} to get the last element, and every collection
     * type had its own way to do it. Java 21 added the {@code SequencedCollection} interface
     * with {@code getFirst()}, {@code getLast()} and {@code reversed()} for every collection
     * that has a defined order ({@code List}, {@code Deque}, {@code LinkedHashSet}, ...).
     *
     * @return the last project in the list
     */
    public ProjectInfo newest() {
        return projects.getLast();
    }

    /**
     * Splits all projects into groups ("pages") of a fixed size.
     *
     * <p><b>Technique: stream gatherers (Java 24, JEP 485).</b> Streams always had built-in
     * intermediate steps such as {@code filter} and {@code map}, but you could not write your
     * own. {@code Stream.gather(...)} adds that extension point. {@link Gatherers} ships
     * ready-made ones; {@link Gatherers#windowFixed(int)} collects consecutive elements into
     * lists of {@code size} elements. With 6 projects and size 4 the result is
     * {@code [[1, 2, 3, 4], [5, 6]]}: the last group may be smaller.
     *
     * @param size number of projects per group, must be at least 1
     * @return the projects grouped in lists of at most {@code size} elements
     * @throws IllegalArgumentException if {@code size} is smaller than 1
     */
    public List<List<ProjectInfo>> inBatches(int size) {
        return projects.stream()
                .gather(Gatherers.windowFixed(size))
                .toList();
    }
}
