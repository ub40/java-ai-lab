package com.jrevolt.javaailab.thread;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ThreadControllerTest {

    @Autowired
    private RestTestClient client;

    @Test
    void currentThreadRunsOnVirtualThread() {
        ThreadController.ThreadInfo info = client.get()
                .uri("/api/thread")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ThreadController.ThreadInfo.class)
                .returnResult()
                .getResponseBody();

        assertThat(info).isNotNull();
        assertThat(info.virtual()).isTrue();
        assertThat(info.name()).startsWith("tomcat-handler-");
        assertThat(info.description()).startsWith("VirtualThread");
    }

    @Test
    void slowThreadSleepsForRequestedTime() {
        long start = System.nanoTime();

        ThreadController.SlowThreadInfo info = client.get()
                .uri("/api/thread/slow?ms=100")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ThreadController.SlowThreadInfo.class)
                .returnResult()
                .getResponseBody();

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(info).isNotNull();
        assertThat(info.ms()).isEqualTo(100);
        assertThat(info.thread().virtual()).isTrue();
        assertThat(info.before()).startsWith("VirtualThread");
        assertThat(info.after()).startsWith("VirtualThread");
        assertThat(elapsedMs).isGreaterThanOrEqualTo(100);
    }
}
