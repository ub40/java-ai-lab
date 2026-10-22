package com.jrevolt.javaailab.action;

import com.jrevolt.javaailab.project.ProjectInfo;

import java.util.List;

/**
 * The result of executing an {@link AiAction}.
 *
 * <p><b>Technique: sealed interface with nested records.</b> Just like {@link AiAction},
 * this is a closed set of variants. Because all permitted records are declared
 * <em>inside</em> this interface (in the same source file), the compiler infers the
 * {@code permits} list automatically, so it can be left out.
 *
 * <p>Each action has its own, precisely typed result instead of a generic {@code Object} or
 * {@code Map}. Spring turns whichever record is returned into JSON, using that record's
 * components as the JSON fields.
 */
public sealed interface ActionResult {

    /**
     * Result of {@link SearchProjects}.
     *
     * @param projects the matching projects, possibly empty
     */
    record ProjectsResult(List<ProjectInfo> projects) implements ActionResult {
    }

    /**
     * Result of {@link GetProject}.
     *
     * @param project the requested project
     */
    record ProjectResult(ProjectInfo project) implements ActionResult {
    }

    /**
     * Result of {@link GenerateSummary}.
     *
     * @param projectId the summarized project
     * @param summary   the generated text
     * @param thread    the thread that produced the summary, so you can see it ran on a
     *                  virtual thread
     */
    record SummaryResult(long projectId, String summary, String thread) implements ActionResult {
    }

    /**
     * Result of {@link GenerateAllSummaries}.
     *
     * @param summaries  one summary per project, in project order
     * @param durationMs total wall-clock time. Because all summaries run in parallel on
     *                   virtual threads, this is close to the time of <em>one</em>
     *                   summary, not the sum of all of them
     */
    record SummariesResult(List<SummaryResult> summaries, long durationMs) implements ActionResult {
    }
}
