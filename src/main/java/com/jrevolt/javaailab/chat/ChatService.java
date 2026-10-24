package com.jrevolt.javaailab.chat;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Stelt een vraag aan het taalmodel (Ollama, via Spring AI) en geeft het antwoord als tekst.
 *
 * <p>De system prompt geldt voor elke vraag; de vraag zelf is de user prompt. Het model
 * onthoudt niets tussen vragen (geen gespreksgeheugen).
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/chatclient.html">Spring AI: ChatClient API</a>
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    /**
     * @param builder door Spring AI geconfigureerd met de instellingen uit {@code application.properties}
     */
    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a helpful Java expert. Answer briefly.")
                .build();
    }

    /**
     * @param question de vraag van de gebruiker
     * @return het antwoord van het model als tekst ({@code content()})
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question) // de user prompt, de vraag van de gebruiker
                .call()//stuurt alles naar Ollama en wacht op het antwoord. Dat wachten gebeurt op een virtual thread
                .content();//haalt alleen de tekst uit het antwoord
    }
}