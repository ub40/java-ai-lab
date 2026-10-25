package com.jrevolt.javaailab.tools;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-endpoint voor de assistent met tools: {@code GET /api/assistant?question=...}.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistantService;

    /**
     * @param assistantService beantwoordt de vraag met behulp van tools
     */
    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /**
     * @param question de vraag
     * @return vraag, antwoord en duur in ms
     */
    @GetMapping
    public AssistantResponse ask(@RequestParam String question) {
        long start = System.nanoTime();
        String answer = assistantService.ask(question);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return new AssistantResponse(question, answer, durationMs);
    }

    /**
     * @param question   de gestelde vraag
     * @param answer     het eindantwoord
     * @param durationMs totale duur, inclusief tool-calls
     */
    public record AssistantResponse(String question, String answer, long durationMs) {
    }

}