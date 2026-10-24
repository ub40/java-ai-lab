package com.jrevolt.javaailab.analysis;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-endpoint voor AI-risicoanalyses: {@code GET /api/projects/{id}/analysis}.
 *
 * <p>Geeft 404 als het project niet bestaat en 502 als het model geen geldig antwoord gaf.
 * Duurt een paar seconden, omdat het op het model wacht. Alleen HTTP; de logica zit in
 * {@link ProjectAnalysisService}.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectAnalysisController {

    private final ProjectAnalysisService analysisService;

    /**
     * @param analysisService voert de analyse uit
     */
    public ProjectAnalysisController(ProjectAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * @param id project-id uit de URL
     * @return de analyse als JSON
     */
    @GetMapping("/{id}/analysis")
    public ProjectAnalysis analysis(@PathVariable long id) {
        return analysisService.analyze(id);
    }
}
