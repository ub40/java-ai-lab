package com.jrevolt.javaailab.action;

import com.jrevolt.javaailab.action.ActionResult.ProjectResult;
import com.jrevolt.javaailab.action.ActionResult.ProjectsResult;
import com.jrevolt.javaailab.action.ActionResult.SummariesResult;
import com.jrevolt.javaailab.action.ActionResult.SummaryResult;
import com.jrevolt.javaailab.project.ProjectNotFoundException;
import com.jrevolt.javaailab.project.ProjectService;
import com.jrevolt.javaailab.project.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AiActionService}, using a real {@link ProjectService} (no mocks needed,
 * because the project service has no external dependencies).
 */
class AiActionServiceTest {

    private final AiActionService service = new AiActionService(new ProjectService());

    @Test
    void searchWithoutStatusReturnsAllProjects() {
        ActionResult result = service.execute(new SearchProjects(null));

        assertThat(result).isInstanceOfSatisfying(ProjectsResult.class,
                r -> assertThat(r.projects()).hasSize(6));
    }

    @Test
    void searchWithStatusFilters() {
        ActionResult result = service.execute(new SearchProjects(ProjectStatus.COMPLETED));

        assertThat(result).isInstanceOfSatisfying(ProjectsResult.class,
                r -> assertThat(r.projects()).singleElement()
                        .satisfies(p -> assertThat(p.id()).isEqualTo(2)));
    }

    @Test
    void getProjectReturnsProject() {
        ActionResult result = service.execute(new GetProject(1));

        assertThat(result).isInstanceOfSatisfying(ProjectResult.class,
                r -> assertThat(r.project().name()).isEqualTo("Java AI Lab"));
    }

    @Test
    void getProjectThrowsForUnknownId() {
        assertThatThrownBy(() -> service.execute(new GetProject(999)))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void generateSummaryDescribesStatus() {
        ActionResult result = service.execute(new GenerateSummary(2));

        assertThat(result).isInstanceOfSatisfying(SummaryResult.class,
                r -> assertThat(r.summary())
                        .isEqualTo("Project 'Virtual Threads Benchmark' has been completed."));
    }

    @Test
    void generateAllSummariesRunsInParallelOnVirtualThreads() {
        ActionResult result = service.execute(new GenerateAllSummaries());

        assertThat(result).isInstanceOfSatisfying(SummariesResult.class, r -> {
            assertThat(r.summaries()).hasSize(6);
            assertThat(r.summaries()).allSatisfy(s -> assertThat(s.thread()).startsWith("VirtualThread"));

            // Sequentially this would take 6 x 300 ms = 1800 ms.
            long sequentialMs = 6 * AiActionService.SIMULATED_AI_LATENCY.toMillis();
            assertThat(r.durationMs()).isLessThan(sequentialMs / 2);
        });
    }

    @Test
    void describeUsesOnlyTheActionType() {
        assertThat(AiActionService.describe(new SearchProjects(null))).isEqualTo("search-projects");
        assertThat(AiActionService.describe(new GetProject(1))).isEqualTo("get-project");
        assertThat(AiActionService.describe(new GenerateSummary(1))).isEqualTo("generate-summary");
        assertThat(AiActionService.describe(new GenerateAllSummaries())).isEqualTo("generate-all-summaries");
    }
}
