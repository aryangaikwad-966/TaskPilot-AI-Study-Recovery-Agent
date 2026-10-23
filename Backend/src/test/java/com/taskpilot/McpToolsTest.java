package com.taskpilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskpilot.mcp.McpClientAdapter;
import com.taskpilot.mcp.TaskMcpTools;
import com.taskpilot.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MCP tool registration and tool-level JSON output.
 *
 * These tests verify that:
 *   - Each tool returns valid JSON with success=true for valid inputs
 *   - Each tool returns success=false with an error for invalid inputs
 *   - Unknown tool arguments are handled safely
 */
@SpringBootTest
class McpToolsTest {

    @Autowired
    private TaskMcpTools taskMcpTools;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        taskService.clearAll();
    }

    // -------------------------------------------------------------------------
    // add_task tool tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MCP add_task: returns valid JSON with task_id on success")
    void testMcpAddTask_success() throws Exception {
        String result = taskMcpTools.add_task("Study Algorithms");

        assertNotNull(result);
        var node = objectMapper.readTree(result);
        assertTrue(node.path("success").asBoolean(), "success should be true");
        assertFalse(node.path("task_id").asText().isBlank(), "task_id should be present");
        assertEquals("Study Algorithms", node.path("title").asText());
        assertEquals("PENDING", node.path("status").asText());
    }

    @Test
    @DisplayName("MCP add_task: returns error JSON for empty title")
    void testMcpAddTask_emptyTitle() throws Exception {
        String result = taskMcpTools.add_task("");

        assertNotNull(result);
        var node = objectMapper.readTree(result);
        assertFalse(node.path("success").asBoolean(), "success should be false");
        assertFalse(node.path("error").asText().isBlank(), "error message should be present");
    }

    @Test
    @DisplayName("MCP add_task: returns error JSON for null title")
    void testMcpAddTask_nullTitle() throws Exception {
        String result = taskMcpTools.add_task(null);

        assertNotNull(result);
        var node = objectMapper.readTree(result);
        assertFalse(node.path("success").asBoolean(), "success should be false");
    }

    // -------------------------------------------------------------------------
    // list_tasks tool tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MCP list_tasks: returns all tasks")
    void testMcpListTasks_all() throws Exception {
        taskService.addTask("Task A");
        taskService.addTask("Task B");

        String result = taskMcpTools.list_tasks("ALL");
        var node = objectMapper.readTree(result);

        assertTrue(node.path("success").asBoolean());
        assertEquals(2, node.path("count").asInt());
        assertTrue(node.path("tasks").isArray());
    }

    @Test
    @DisplayName("MCP list_tasks: returns empty list when no tasks")
    void testMcpListTasks_empty() throws Exception {
        String result = taskMcpTools.list_tasks("ALL");
        var node = objectMapper.readTree(result);

        assertTrue(node.path("success").asBoolean());
        assertEquals(0, node.path("count").asInt());
    }

    @Test
    @DisplayName("MCP list_tasks: handles null status as ALL")
    void testMcpListTasks_nullStatus() throws Exception {
        taskService.addTask("Test Task");
        String result = taskMcpTools.list_tasks(null);
        var node = objectMapper.readTree(result);
        assertTrue(node.path("success").asBoolean());
        assertEquals(1, node.path("count").asInt());
    }

    @Test
    @DisplayName("MCP list_tasks: returns error for invalid status")
    void testMcpListTasks_invalidStatus() throws Exception {
        String result = taskMcpTools.list_tasks("BOGUS_STATUS");
        var node = objectMapper.readTree(result);
        assertFalse(node.path("success").asBoolean());
        assertFalse(node.path("error").asText().isBlank());
    }

    // -------------------------------------------------------------------------
    // complete_task tool tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MCP complete_task: marks task completed successfully")
    void testMcpCompleteTask_success() throws Exception {
        var task = taskService.addTask("Finish DSA Practice");
        String result = taskMcpTools.complete_task(task.getId());
        var node = objectMapper.readTree(result);

        assertTrue(node.path("success").asBoolean());
        assertEquals("COMPLETED", node.path("status").asText());
        assertEquals(task.getId(), node.path("task_id").asText());
    }

    @Test
    @DisplayName("MCP complete_task: returns error for unknown ID")
    void testMcpCompleteTask_unknownId() throws Exception {
        String result = taskMcpTools.complete_task("task-does-not-exist");
        var node = objectMapper.readTree(result);

        assertFalse(node.path("success").asBoolean());
        assertFalse(node.path("error").asText().isBlank());
        assertTrue(node.path("error").asText().contains("task-does-not-exist"));
    }

    @Test
    @DisplayName("MCP complete_task: returns error for empty ID")
    void testMcpCompleteTask_emptyId() throws Exception {
        String result = taskMcpTools.complete_task("");
        var node = objectMapper.readTree(result);

        assertFalse(node.path("success").asBoolean());
        assertFalse(node.path("error").asText().isBlank());
    }
}
