package com.taskpilot.service;

/**
 * Chat service interface — allows mocking in tests without byte-buddy issues.
 * Implemented by GeminiService.
 */
public interface ChatService {
    /**
     * Process a natural-language user message and return a reply.
     *
     * @param userMessage The user's message
     * @return Human-readable reply string
     */
    String chat(String userMessage);
}
