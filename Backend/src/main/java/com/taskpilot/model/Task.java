package com.taskpilot.model;

import java.time.Instant;

/**
 * Represents a study task stored in memory.
 * Tasks reset after application restart (in-memory only).
 */
public class Task {

    public enum Status { PENDING, COMPLETED }

    private final String id;
    private final String title;
    private Status status;
    private final Instant createdAt;
    private Instant completedAt;

    public Task(String id, String title) {
        this.id = id;
        this.title = title;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }

    // --- Getters ---

    public String getId() { return id; }

    public String getTitle() { return title; }

    public Status getStatus() { return status; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getCompletedAt() { return completedAt; }

    // --- Mutation ---

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "Task{id='" + id + "', title='" + title + "', status=" + status + "}";
    }
}
