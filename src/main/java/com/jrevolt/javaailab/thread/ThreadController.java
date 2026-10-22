package com.jrevolt.javaailab.thread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.lang.management.ThreadInfo;

@RestController
@RequestMapping("/api/thread")
public class ThreadController {

    @GetMapping
    public ThreadInfo currentThread() {

        Thread thread = Thread.currentThread();

        return new ThreadInfo(
                thread.threadId(),
                thread.getName(),
                thread.isVirtual(),
                thread.getState().name()
        );
    }

    public record ThreadInfo(
            long id,
            String name,
            boolean virtual,
            String state
    ) {
    }
}