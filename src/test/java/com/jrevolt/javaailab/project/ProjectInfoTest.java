package com.jrevolt.javaailab.project;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProjectInfo}: no Spring, just plain Java, so they run in milliseconds.
 */
class ProjectInfoTest {

    @Test
    void compactConstructorStripsName() {
        ProjectInfo project = new ProjectInfo(1, "  Java AI Lab  ", ProjectStatus.PLANNED);

        assertThat(project.name()).isEqualTo("Java AI Lab");
    }

    @Test
    void compactConstructorRejectsBlankName() {
        assertThatThrownBy(() -> new ProjectInfo(1, "   ", ProjectStatus.PLANNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void compactConstructorRejectsNonPositiveId() {
        assertThatThrownBy(() -> new ProjectInfo(0, "Java AI Lab", ProjectStatus.PLANNED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compactConstructorRejectsNullStatus() {
        assertThatThrownBy(() -> new ProjectInfo(1, "Java AI Lab", (ProjectStatus) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void flexibleConstructorParsesStatusText() {
        ProjectInfo project = new ProjectInfo(1, "Java AI Lab", " in_progress ");

        assertThat(project.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(project.active()).isTrue();
    }

    @Test
    void flexibleConstructorRejectsUnknownStatus() {
        assertThatThrownBy(() -> new ProjectInfo(1, "Java AI Lab", "almost-done"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsWithSameValuesAreEqual() {
        assertThat(new ProjectInfo(1, "A", ProjectStatus.PLANNED))
                .isEqualTo(new ProjectInfo(1, "A", "planned"));
    }
}
