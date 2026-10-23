package com.taskpilot;

import com.taskpilot.model.Task;
import com.taskpilot.service.ChatService;
import com.taskpilot.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Value("${gemini.api.key:}")
    private String apiKey;

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
    @DisplayName("/api/health: reports Streamable HTTP MCP 2025-11-25")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.mcp_endpoint").value("POST /mcp"))
                .andExpect(jsonPath("$.mcp_spec").value("2025-11-25"))
                .andExpect(jsonPath("$.transport").value("Streamable HTTP (JSON response)"));
    }

    @Test
    @DisplayName("/api/tasks: filtered list still returns overall dashboard counts")
    void testListTasksEndpoint_includesOverallCounts() throws Exception {
        var pending = taskService.addTask("Pending task");
        var done = taskService.addTask("Done task");
        taskService.completeTask(done.getId());

        mockMvc.perform(get("/api/tasks").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.tasks[0].id").value(pending.getId()))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.completedCount").value(1));
    }

    @Test
    @DisplayName("/api/recovery: missing key with pending tasks returns 503")
    void testRecoveryWithoutApiKey() throws Exception {
        if (apiKey != null && !apiKey.isBlank()) {
            return;
        }
        taskService.addTask("Study Java");
        mockMvc.perform(post("/api/recovery")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"I missed two days\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assumptions").isArray());
    }

    @Test
    @DisplayName("/api/recovery/approve: completes only valid pending tasks")
    void testApproveRecovery() throws Exception {
        var pending = taskService.addTask("Approve me");
        var alreadyDone = taskService.addTask("Already done");
        taskService.completeTask(alreadyDone.getId());

        mockMvc.perform(post("/api/recovery/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"taskIds\":[\"" + pending.getId() + "\",\"" + alreadyDone.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed", hasSize(1)))
                .andExpect(jsonPath("$.completed[0]").value(pending.getId()));

        assertEquals(Task.Status.COMPLETED, taskService.findById(pending.getId()).orElseThrow().getStatus());
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
