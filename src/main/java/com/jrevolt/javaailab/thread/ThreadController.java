package com.jrevolt.javaailab.thread;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Laat zien op welke thread een request draait, om virtual threads te onderzoeken.
 *
 * <ul>
 *   <li>{@code GET /api/thread}: info over de huidige thread</li>
 *   <li>{@code GET /api/thread/slow?ms=1000}: wacht {@code ms} milliseconden (nagebootste
 *       I/O) en toont de carrier vóór en na het wachten</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/thread")
public class ThreadController {

    /**
     * @return info over de thread die dit request afhandelt
     */
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

    /**
     * Bootst een trage I/O-call na. Bij drukte kan {@code after} op een andere carrier staan dan
     * {@code before}: bewijs van unmount en mount.
     *
     * @param ms hoe lang te wachten
     * @return thread-info plus de thread vóór en na het wachten
     */
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

    /**
     * @param id          thread-id
     * @param name        naam, bijv. {@code tomcat-handler-0} (virtual) of {@code http-nio-8080-exec-1} (platform)
     * @param virtual     of het een virtual thread is
     * @param state       status van de thread
     * @param description {@code toString()}, met bij een virtual thread ook de carrier
     */
    public record ThreadInfo(
            long id,
            String name,
            boolean virtual,
            String state,
            String description
    ) {
    }

    /**
     * @param thread de thread-info na het wachten
     * @param before {@code toString()} vóór het wachten
     * @param after  {@code toString()} na het wachten
     * @param ms     hoe lang gewacht is
     */
    public record SlowThreadInfo(
            ThreadInfo thread,
            String before,
            String after,
            long ms
    ) {
    }
}