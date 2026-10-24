package com.jrevolt.javaailab.analysis;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for AI-generated project risk analyses.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>{@code GET /api/projects/{id}/analysis}: the model's risk assessment of one project,
 *       for example {@code {"risk":"MEDIUM","summary":"...","recommendedAction":"..."}}.
 *       Returns HTTP 404 if the project does not exist. Takes a few seconds, because it waits
 *       for the language model.</li>
 * </ul>
 *
 * <p>This controller shares the base path {@code /api/projects} with
 * {@code ProjectController}. That is allowed in Spring MVC as long as the full paths differ.
 * Keeping it separate keeps the AI-dependent endpoint out of the plain project API.
 *
 * <p><b>Responsibility:</b> only HTTP. All logic lives in {@link ProjectAnalysisService}.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectAnalysisController {

    private final ProjectAnalysisService analysisService;

    /**
     * @param analysisService performs the analysis, injected by Spring
     */
    public ProjectAnalysisController(ProjectAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * @param id the project id from the URL
     * @return the analysis as JSON; Spring converts the {@link ProjectAnalysis} record
     */
    @GetMapping("/{id}/analysis")
    public ProjectAnalysis analysis(@PathVariable long id) {
        return analysisService.analyze(id);
    }
}
