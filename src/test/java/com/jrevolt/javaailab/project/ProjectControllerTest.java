package com.jrevolt.javaailab.project;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests: the real application starts on a random port and the tests send real
 * HTTP requests. {@code jsonPath} checks fields in the JSON response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProjectControllerTest {

    @Autowired
    private RestTestClient client;

    @Test
    void searchReturnsAllProjects() {
        client.get().uri("/api/projects")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.projects.length()").isEqualTo(6);
    }

    @Test
    void searchFiltersByStatus() {
        client.get().uri("/api/projects?status=IN_PROGRESS")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.projects.length()").isEqualTo(2)
                .jsonPath("$.projects[0].status").isEqualTo("IN_PROGRESS");
    }

    @Test
    void searchWithUnknownStatusIsBadRequest() {
        client.get().uri("/api/projects?status=ALMOST_DONE")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getReturnsProject() {
        client.get().uri("/api/projects/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.project.name").isEqualTo("Java AI Lab");
    }

    @Test
    void getUnknownProjectIsNotFound() {
        client.get().uri("/api/projects/999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void summaryRunsOnVirtualThread() {
        client.get().uri("/api/projects/3/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summary").isEqualTo("Project 'Spring AI with Ollama' is planned and has not started yet.")
                .jsonPath("$.thread").value(String.class, thread -> assertThat(thread).startsWith("VirtualThread"));
    }

    @Test
    void summariesReturnsOneSummaryPerProject() {
        client.get().uri("/api/projects/summaries")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summaries.length()").isEqualTo(6)
                .jsonPath("$.durationMs").exists();
    }

    @Test
    void newestReturnsLastProject() {
        client.get().uri("/api/projects/newest")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(6);
    }

    @Test
    void batchesGroupsProjects() {
        client.get().uri("/api/projects/batches?size=4")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].length()").isEqualTo(4)
                .jsonPath("$[1].length()").isEqualTo(2);
    }

    @Test
    void batchesRejectsInvalidSize() {
        client.get().uri("/api/projects/batches?size=0")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
