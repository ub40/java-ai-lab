package com.jrevolt.javaailab.thread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/thread")
public class ThreadController {

    @GetMapping
    public ThreadInfo currentThread() {
        return currentThreadInfo();
    }

    private ThreadInfo currentThreadInfo() {
        Thread thread = Thread.currentThread();
        return new ThreadInfo(
                thread.threadId(),
                thread.getName(),
                thread.isVirtual(),
                thread.getState().name(),
                thread.toString()
        );
    }

    @GetMapping("/slow")
    public SlowThreadInfo slowThread(@RequestParam(defaultValue = "1000") long ms){
        String before = Thread.currentThread().toString();
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sleeping", e);
        }

        String after = Thread.currentThread().toString();
        return new SlowThreadInfo(currentThreadInfo(), before, after, ms);
    }

    public record ThreadInfo(
            long id,
            String name,
            boolean virtual,
            String state,
            String description
    ) {
    }

    public record SlowThreadInfo(
            ThreadInfo thread,
            String before,
            String after,
            long ms
    ) {
    }
}