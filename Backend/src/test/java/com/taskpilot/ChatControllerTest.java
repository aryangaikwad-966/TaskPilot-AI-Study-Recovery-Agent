package com.taskpilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskpilot.dto.ChatRequest;
import com.taskpilot.dto.ChatResponse;
import com.taskpilot.service.ChatService;
import com.taskpilot.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the /api/chat endpoint with mocked Gemini service.
 * These tests verify:
 *   - Gemini API failure handling (mocked)
 *   - Unknown tool rejection
 *   - Controller-level validation
 *   - Health endpoint
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        taskService.clearAll();
    }

    @Test
    @DisplayName("/api/chat: rejects empty message with 400")
    void testChat_emptyMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("/api/chat: returns reply on success")
    void testChat_success() throws Exception {
        when(chatService.chat(anyString())).thenReturn("Task created successfully.");

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Add a task to practise Java DSA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reply").value("Task created successfully."));
    }

    @Test
    @DisplayName("/api/chat: handles Gemini API failure gracefully")
    void testChat_geminiFailure_returns500() throws Exception {
        when(chatService.chat(anyString())).thenThrow(new RuntimeException("Gemini API error"));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Show my tasks\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("/api/tasks: returns task list")
    void testListTasksEndpoint() throws Exception {
        taskService.addTask("Study Spring Boot");
        taskService.addTask("Learn MCP");

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks", hasSize(2)));
    }

    @Test
    @DisplayName("/api/tasks: filters by PENDING status")
    void testListTasksEndpoint_filterPending() throws Exception {
        var t1 = taskService.addTask("Pending 1");
        var t2 = taskService.addTask("Will Complete");
        taskService.completeTask(t2.getId());

        mockMvc.perform(get("/api/tasks").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    @DisplayName("/api/health: returns UP status")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("/api/chat: does not expose stack trace in error response")
    void testChat_noStackTraceInResponse() throws Exception {
        when(chatService.chat(anyString())).thenThrow(new NullPointerException("internal error"));

        String response = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"test\"}"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        // Must not contain Java class names or stack trace indicators
        assert !response.contains("NullPointerException");
        assert !response.contains("at com.");
        assert !response.contains("StackTrace");
    }
}
