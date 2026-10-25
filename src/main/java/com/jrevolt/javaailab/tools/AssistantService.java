package com.jrevolt.javaailab.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Een assistent die vragen over projecten beantwoordt met behulp van tools.
 *
 * <p><b>Techniek: tool calling.</b> Het model krijgt de tools uit {@link ProjectTools} en
 * beslist zelf of het er een nodig heeft. Spring AI voert de gevraagde tool uit, stuurt het
 * resultaat terug naar het model, en geeft het eindantwoord terug.
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/tools.html">Spring AI: Tool Calling</a>
 */
@Service
public class AssistantService {
    private final ChatClient chatClient;

    /**
     * @param builder      door Spring AI geconfigureerd
     * @param projectTools de tools die het model mag gebruiken
     */
    public AssistantService(ChatClient.Builder builder, ProjectTools projectTools) {
        this.chatClient = builder
                .defaultSystem("""                                                                                                                                                                           
                          You are an assistant for a software team.                                                                                                                                            
                          Use the available tools to look up project data.                                                                                                                                     
                          Never invent project data; if a tool returns no data, say so.                                                                                                                        
                          """)
                .defaultTools(projectTools)
                .build();
    }

    /**
     * @param question de vraag van de gebruiker
     * @return het eindantwoord van het model, eventueel op basis van tool-resultaten
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}