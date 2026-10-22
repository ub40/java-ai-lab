package com.jrevolt.javaailab.thread;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.threads.virtual.enabled=false"
)
@AutoConfigureRestTestClient
class ThreadControllerPlatformThreadTest {

    @Autowired
    private RestTestClient client;

    @Test
    void currentThreadRunsOnPlatformThreadWhenVirtualThreadsDisabled() {
        ThreadController.ThreadInfo info = client.get()
                .uri("/api/thread")
                .exchange()
                .expectStatus().isOk()
                .expectBody(ThreadController.ThreadInfo.class)
                .returnResult()
                .getResponseBody();

        assertThat(info).isNotNull();
        assertThat(info.virtual()).isFalse();
        assertThat(info.name()).startsWith("http-nio-");
    }
}
