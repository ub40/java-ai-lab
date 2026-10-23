package com.jrevolt.javaailab.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ChatResponse chat(@RequestParam String question) {
        long start = System.nanoTime();
        String answer = chatService.ask(question);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return new ChatResponse(question, answer, durationMs, Thread.currentThread().toString());
    }

    public record ChatResponse(
            String question,
            String answer,
            long durationMs,
            String thread
    ) {
    }
}
