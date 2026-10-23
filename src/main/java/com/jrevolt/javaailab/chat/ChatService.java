package com.jrevolt.javaailab.chat;

import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("You are a helpful Java expert. Answer briefly.")
                .build();
    }

    public String ask(String question) {
        return chatClient.prompt()
                .user(question) // de user prompt, de vraag van de gebruiker
                .call()//stuurt alles naar Ollama en wacht op het antwoord. Dat wachten gebeurt op een virtual thread
                .content();//haalt alleen de tekst uit het antwoord
    }
}