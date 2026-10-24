package com.jrevolt.javaailab.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-endpoint om vragen aan het lokale taalmodel te stellen: {@code GET /api/chat?question=...}.
 *
 * <p>Geeft ook de duur en de thread terug, om de snelheid van het model (inference) te meten en
 * te zien dat het wachten op een virtual thread gebeurt.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    /**
     * @param chatService stelt de vraag aan het model
     */
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * @param question de vraag
     * @return vraag, antwoord, duur in ms en de thread
     */
    @GetMapping
    public ChatResponse chat(@RequestParam String question) {
        long start = System.nanoTime();
        String answer = chatService.ask(question);
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        return new ChatResponse(question, answer, durationMs, Thread.currentThread().toString());
    }

    /**
     * @param question   de gestelde vraag
     * @param answer     het antwoord van het model
     * @param durationMs hoe lang het model erover deed
     * @param thread     de thread die het verzoek afhandelde
     */
    public record ChatResponse(
            String question,
            String answer,
            long durationMs,
            String thread
    ) {
    }
}
