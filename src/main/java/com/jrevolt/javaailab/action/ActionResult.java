package com.jrevolt.javaailab.action;

import com.jrevolt.javaailab.project.ProjectInfo;

import java.util.List;

/**
 * Het resultaat van een {@link AiAction}.
 *
 * <p><b>Techniek: sealed interface met geneste records.</b> Omdat de records in hetzelfde
 * bestand staan, leidt de compiler de {@code permits}-lijst zelf af. Elke actie krijgt een
 * precies getypeerd resultaat in plaats van {@code Object} of {@code Map}.
 */
public sealed interface ActionResult {

    /**
     * Resultaat van {@link SearchProjects}.
     *
     * @param projects de gevonden projecten, mogelijk leeg
     */
    record ProjectsResult(List<ProjectInfo> projects) implements ActionResult {
    }

    /**
     * Resultaat van {@link GetProject}.
     *
     * @param project het gevraagde project
     */
    record ProjectResult(ProjectInfo project) implements ActionResult {
    }

    /**
     * Resultaat van {@link GenerateSummary}.
     *
     * @param projectId het samengevatte project
     * @param summary   de tekst
     * @param thread    de thread die het maakte, om de virtual thread te zien
     */
    record SummaryResult(long projectId, String summary, String thread) implements ActionResult {
    }

    /**
     * Resultaat van {@link GenerateAllSummaries}.
     *
     * @param summaries  één samenvatting per project
     * @param durationMs totale duur; door parallel werken ongeveer de tijd van één samenvatting
     */
    record SummariesResult(List<SummaryResult> summaries, long durationMs) implements ActionResult {
    }
}
