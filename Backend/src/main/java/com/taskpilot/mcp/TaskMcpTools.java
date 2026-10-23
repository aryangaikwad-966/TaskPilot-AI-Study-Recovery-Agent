package com.taskpilot.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskpilot.model.Task;
import com.taskpilot.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool definitions for TaskPilot.
 * These methods are registered by McpProtocolController and exposed through
 * the MCP 2025-11-25 Streamable HTTP endpoint at POST /mcp.
 */
@Component
public class TaskMcpTools {

    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskMcpTools(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    /**
     * Tool A: add_task
     * Creates a new study task with PENDING status.
     */
    public String add_task(String title) {
        try {
            Task task = taskService.addTask(title);
            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "task_id", task.getId(),
                "title", task.getTitle(),
                "status", task.getStatus().name(),
                "message", "Task '" + task.getTitle() + "' created with ID " + task.getId()
            ));
        } catch (IllegalArgumentException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            return buildError("Failed to add task: " + e.getMessage());
        }
    }

    /**
     * Tool B: list_tasks
     * Returns tasks filtered by status (ALL, PENDING, or COMPLETED).
     */
    public String list_tasks(String status) {
        try {
            String filter = (status == null || status.isBlank()) ? "ALL" : status;
            List<Task> tasks = taskService.listTasks(filter);
            List<Map<String, Object>> taskList = tasks.stream()
                .map(t -> Map.<String, Object>of(
                    "task_id", t.getId(),
                    "title", t.getTitle(),
                    "status", t.getStatus().name(),
                    "created_at", t.getCreatedAt().toString()
                ))
                .toList();
            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "count", taskList.size(),
                "filter", filter,
                "tasks", taskList
            ));
        } catch (IllegalArgumentException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            return buildError("Failed to list tasks: " + e.getMessage());
        }
    }

    /**
     * Tool C: complete_task
     * Marks a task as COMPLETED by its ID.
     */
    public String complete_task(String task_id) {
        try {
            Task task = taskService.completeTask(task_id);
            return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "task_id", task.getId(),
                "title", task.getTitle(),
                "status", task.getStatus().name(),
                "message", "Task '" + task.getTitle() + "' marked as COMPLETED."
            ));
        } catch (IllegalArgumentException e) {
            return buildError(e.getMessage());
        } catch (Exception e) {
            return buildError("Failed to complete task: " + e.getMessage());
        }
    }

    private String buildError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                "success", false,
                "error", message
            ));
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
}
