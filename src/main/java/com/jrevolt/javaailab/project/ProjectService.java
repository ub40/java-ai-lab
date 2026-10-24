package com.jrevolt.javaailab.project;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Gatherers;

/**
 * Logica en data-toegang voor projecten.
 *
 * <p>Een controller doet alleen HTTP; de logica zit hier, zodat controllers, AI-tools en
 * tests dezelfde code gebruiken. {@link Service @Service} maakt hiervan één Spring-bean.
 *
 * <p>De data is een lijst in het geheugen (nog geen database). Omdat de lijst onveranderbaar
 * is en {@link ProjectInfo} een record is, is deze klasse thread-safe zonder locks.
 */
@Service
public class ProjectService {

    /**
     * De "database". {@link List#of} is onveranderbaar; de volgorde is de volgorde van toevoegen,
     * dus het laatste element is het nieuwste project.
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
     * @return alle projecten, in volgorde van toevoegen
     */
    public List<ProjectInfo> findAll() {
        return projects;
    }

    /**
     * Zoekt een project op id. {@link Optional} maakt "misschien geen resultaat" zichtbaar in de
     * signatuur, in plaats van een onverwachte {@code null}.
     *
     * @param id het project-id
     * @return het project, of een lege {@code Optional}
     */
    public Optional<ProjectInfo> findById(long id) {
        return projects.stream()
                .filter(project -> project.id() == id)
                .findFirst();
    }

    /**
     * Zoals {@link #findById(long)}, maar een ontbrekend project is een fout.
     *
     * @param id het project-id
     * @return het project
     * @throws ProjectNotFoundException als het project niet bestaat (HTTP 404)
     */
    public ProjectInfo getById(long id) {
        return findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    /**
     * @param status de status om op te filteren
     * @return alle projecten met die status, mogelijk leeg
     */
    public List<ProjectInfo> findByStatus(ProjectStatus status) {
        return projects.stream()
                .filter(project -> project.status() == status)
                .toList();
    }

    /**
     * <b>Techniek: sequenced collections (Java 21, JEP 431).</b> {@code getLast()} in plaats van
     * {@code list.get(list.size() - 1)}; werkt op elke geordende collectie.
     *
     * @return het laatst toegevoegde project
     */
    public ProjectInfo newest() {
        return projects.getLast();
    }

    /**
     * Verdeelt de projecten in groepjes van vaste grootte.
     *
     * <p><b>Techniek: stream gatherers (Java 24, JEP 485).</b> Met {@code gather(...)} kun je
     * eigen tussenstappen in een stream gebruiken. {@link Gatherers#windowFixed(int)} maakt
     * groepjes: 6 projecten met grootte 4 geeft {@code [[1, 2, 3, 4], [5, 6]]}.
     *
     * @param size projecten per groep, minstens 1
     * @return de groepjes; de laatste kan kleiner zijn
     * @throws IllegalArgumentException als {@code size} kleiner dan 1 is
     */
    public List<List<ProjectInfo>> inBatches(int size) {
        return projects.stream()
                .gather(Gatherers.windowFixed(size))
                .toList();
    }
}
