package com.jrevolt.javaailab.tools;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Biedt de {@link ProjectTools} aan als MCP-tools, zodat elke MCP-client (bijv. Claude Code)
 * ze kan aanroepen.
 *
 * <p>Het zijn dezelfde tools als voor de eigen assistent: één implementatie, twee ingangen
 * (tool calling binnen de app, en MCP voor externe AI-applicaties).
 *
 * @see <a href="https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html">Spring AI: MCP Server Boot Starter</a>
 */
@Configuration
public class McpServerConfig {

    /**
     * @param projectTools de bestaande tools met {@code @Tool}-methodes
     * @return de tools in een vorm die de MCP-server kan registreren
     */
    @Bean
    public ToolCallbackProvider projectToolCallbacks(ProjectTools projectTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(projectTools)
                .build();
    }
}
