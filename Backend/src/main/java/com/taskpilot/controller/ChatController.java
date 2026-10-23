package com.taskpilot.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.taskpilot.dto.ChatRequest;
import com.taskpilot.dto.ChatResponse;
import com.taskpilot.dto.RecoveryRequest;
import com.taskpilot.dto.RecoveryResponse;
import com.taskpilot.model.Task;
import com.taskpilot.service.ChatService;
import com.taskpilot.service.RecoveryService;
import com.taskpilot.service.TaskService;

/**
 * REST controller for the browser UI.
 *
 * Endpoints:
 *   POST /api/chat        – Process natural-language request via Gemini + MCP
 *   GET  /api/tasks       – Direct task list (bypasses Gemini, for UI task panel)
 *   POST /api/tasks       – Create a new task
 *   PATCH /api/tasks/{id}/complete – Mark a task as completed
 *   POST /api/recovery    – Generate a recovery proposal
 *   POST /api/recovery/approve – Approve and execute a recovery plan
 *   GET  /api/health      – Health check
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final TaskService taskService;
    private final RecoveryService recoveryService;

    public ChatController(ChatService chatService, TaskService taskService, RecoveryService recoveryService) {
        this.chatService = chatService;
        this.taskService = taskService;
        this.recoveryService = recoveryService;
    }

    /**
     * Main chat endpoint. Accepts a natural-language message and returns
     * the AI agent's reply after executing any required task operations.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.fail("Message must not be empty."));
        }

        try {
            String reply = chatService.chat(request.getMessage());
            return ResponseEntity.ok(ChatResponse.ok(reply));
        } catch (Exception e) {
            // Never expose stack traces or internal details to the frontend
            return ResponseEntity.internalServerError()
                    .body(ChatResponse.fail("An internal error occurred. Please try again."));
        }
    }

    /**
     * Returns current task list for the UI task panel.
     * This is a direct service call (not through Gemini) to keep the UI responsive.
     */
    @GetMapping("/tasks")
    public ResponseEntity<?> listTasks(@RequestParam(defaultValue = "ALL") String status) {
        try {
            List<Task> tasks = taskService.listTasks(status);
            List<Map<String, String>> result = tasks.stream()
                .map(t -> Map.of(
                    "id", t.getId(),
                    "title", t.getTitle(),
                    "status", t.getStatus().name(),
                    "createdAt", t.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of(
                "tasks", result,
                "count", result.size(),
                "pendingCount", taskService.countByStatus(Task.Status.PENDING),
                "completedCount", taskService.countByStatus(Task.Status.COMPLETED)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Creates a new task.
     */
    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@RequestBody Map<String, String> request) {
        try {
            String title = request.get("title");
            if (title == null || title.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Task title must not be empty."));
            }
            Task task = taskService.addTask(title);
            return ResponseEntity.status(201).body(Map.of(
                "id", task.getId(),
                "title", task.getTitle(),
                "status", task.getStatus().name(),
                "createdAt", task.getCreatedAt().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Marks a task as completed.
     */
    @PatchMapping("/tasks/{id}/complete")
    public ResponseEntity<?> completeTask(@PathVariable String id) {
        try {
            Task task = taskService.completeTask(id);
            return ResponseEntity.ok(Map.of(
                "id", task.getId(),
                "title", task.getTitle(),
                "status", task.getStatus().name(),
                "createdAt", task.getCreatedAt().toString()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Generates a recovery proposal based on the user's request.
     */
    @PostMapping("/recovery")
    public ResponseEntity<?> generateRecovery(@RequestBody RecoveryRequest request) {
        try {
            if (request.getMessage() == null || request.getMessage().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Message must not be empty."));
            }
            RecoveryResponse response = recoveryService.generateRecovery(request.getMessage());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to generate recovery plan."));
        }
    }

    /**
     * Approves and executes a recovery plan by completing specified tasks.
     */
    @PostMapping("/recovery/approve")
    public ResponseEntity<?> approveRecovery(@RequestBody Map<String, List<String>> request) {
        try {
            List<String> taskIds = request.get("taskIds");
            if (taskIds == null || taskIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Task IDs must not be empty."));
            }

            if (taskIds.stream().anyMatch(id -> id == null || taskService.findById(id).isEmpty())) {
                return ResponseEntity.badRequest().body(Map.of("error", "One or more task IDs are invalid."));
            }

            List<String> completedIds = taskIds.stream()
                    .distinct()
                    .filter(id -> taskService.findById(id).map(t -> t.getStatus() == Task.Status.PENDING).orElse(false))
                    .peek(taskService::completeTask)
                    .toList();

            return ResponseEntity.ok(Map.of("completed", completedIds));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to approve recovery plan."));
        }
    }

    /**
     * Health check — used to confirm the server is running.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "TaskPilot",
            "mcp_endpoint", "POST /mcp",
            "mcp_spec", "2025-11-25",
            "transport", "Streamable HTTP (JSON response)"
        ));
    }
}
