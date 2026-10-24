package com.jrevolt.javaailab.project;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-tests voor {@link ProjectService}, aangemaakt met {@code new}, zonder Spring.
 */
class ProjectServiceTest {

    private final ProjectService service = new ProjectService();

    @Test
    void findAllReturnsAllProjects() {
        assertThat(service.findAll()).hasSize(6);
    }

    @Test
    void findByIdReturnsEmptyOptionalForUnknownId() {
        assertThat(service.findById(999)).isEmpty();
    }

    @Test
    void getByIdThrowsForUnknownId() {
        assertThatThrownBy(() -> service.getById(999))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project 999 not found");
    }

    @Test
    void findByStatusFiltersProjects() {
        assertThat(service.findByStatus(ProjectStatus.IN_PROGRESS))
                .extracting(ProjectInfo::id)
                .containsExactly(1L, 5L);
    }

    @Test
    void newestReturnsLastAddedProject() {
        assertThat(service.newest().id()).isEqualTo(6);
    }

    @Test
    void inBatchesGroupsProjectsAndLastGroupMayBeSmaller() {
        List<List<ProjectInfo>> batches = service.inBatches(4);

        assertThat(batches).hasSize(2);
        assertThat(batches.getFirst()).hasSize(4);
        assertThat(batches.getLast()).hasSize(2);
    }
}
