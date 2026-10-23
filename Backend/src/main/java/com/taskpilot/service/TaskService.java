package com.taskpilot.service;

import com.taskpilot.model.Task;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Core business logic for task management.
 * Uses a ConcurrentHashMap as in-memory storage.
 * Both the MCP server tools and any direct calls share this single instance.
 *
 * NOTE: All data is lost on application restart (by design).
 */
@Service
public class TaskService {

    private final Map<String, Task> taskStore = new ConcurrentHashMap<>();

    /**
     * Creates a new task with PENDING status.
     *
     * @param title Non-empty task title
     * @return The newly created Task
     * @throws IllegalArgumentException if the title is blank
     */
    public Task addTask(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title must not be empty.");
        }
        if (title.trim().length() > 300) {
            throw new IllegalArgumentException("Task title must be 300 characters or fewer.");
        }
        String id = "task-" + UUID.randomUUID();
        Task task = new Task(id, title.trim());
        taskStore.put(id, task);
        return task;
    }

    /**
     * Returns tasks filtered by status string.
     *
     * @param statusFilter "ALL", "PENDING", "COMPLETED", or null (treated as ALL)
     * @return Filtered list of tasks
     */
    public List<Task> listTasks(String statusFilter) {
        List<Task> all = new ArrayList<>(taskStore.values());
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return all;
        }
        try {
            Task.Status target = Task.Status.valueOf(statusFilter.toUpperCase());
            return all.stream()
                    .filter(t -> t.getStatus() == target)
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid status filter '" + statusFilter + "'. Use ALL, PENDING, or COMPLETED.");
        }
    }

    /**
     * Marks an existing task as COMPLETED.
     *
     * @param taskId The task ID to complete
     * @return The updated Task
     * @throws IllegalArgumentException if the task ID is not found
     */
    public Task completeTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID must not be empty.");
        }
        Task task = taskStore.get(taskId.trim());
        if (task == null) {
            throw new IllegalArgumentException("No task found with ID: " + taskId);
        }
        task.markCompleted();
        return task;
    }

    /**
     * Finds a task by ID (used for testing/inspection).
     */
    public Optional<Task> findById(String taskId) {
        return Optional.ofNullable(taskStore.get(taskId));
    }

    /** Returns a count of all stored tasks (used in tests). */
    public int count() {
        return taskStore.size();
    }

    /** Clears all tasks — for test isolation only. */
    public void clearAll() {
        taskStore.clear();
    }
}
