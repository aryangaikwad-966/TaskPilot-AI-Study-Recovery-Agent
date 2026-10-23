package com.taskpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TaskPilot – AI-powered study task agent.
 * Amazon Developer Hackathon 2026 (Alexa+ track).
 *
 * Architecture:
 *   Browser → /api/chat → GeminiService (function calling) →
 *   McpClientAdapter → MCP Server (Streamable HTTP) → TaskService
 */
@SpringBootApplication
public class TaskPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskPilotApplication.class, args);
    }
}
