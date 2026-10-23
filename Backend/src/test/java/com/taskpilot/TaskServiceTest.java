package com.taskpilot;

import com.taskpilot.model.Task;
import com.taskpilot.service.TaskService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TaskService.
 * Tests run against real in-memory state; each test class gets a clean slate.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService.clearAll();
    }

    // -------------------------------------------------------------------------
    // Test 1: Successful task creation
    // -------------------------------------------------------------------------
    @Test
    @Order(1)
    @DisplayName("add_task: creates task with PENDING status")
    void testAddTask_success() {
        Task task = taskService.addTask("Practise Java DSA");

        assertNotNull(task, "Task should not be null");
        assertNotNull(task.getId(), "Task ID should not be null");
        assertTrue(task.getId().startsWith("task-"), "ID should start with 'task-'");
        assertEquals("Practise Java DSA", task.getTitle());
        assertEquals(Task.Status.PENDING, task.getStatus());
        assertNotNull(task.getCreatedAt());
        assertNull(task.getCompletedAt());
    }

    // -------------------------------------------------------------------------
    // Test 2: Empty-title rejection
    // -------------------------------------------------------------------------
    @Test
    @Order(2)
    @DisplayName("add_task: rejects empty title")
    void testAddTask_emptyTitle_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> taskService.addTask(""));
        assertThrows(IllegalArgumentException.class, () -> taskService.addTask("   "));
        assertThrows(IllegalArgumentException.class, () -> taskService.addTask(null));
    }

    // -------------------------------------------------------------------------
    // Test 3: List all tasks
    // -------------------------------------------------------------------------
    @Test
    @Order(3)
    @DisplayName("list_tasks: returns all tasks")
    void testListTasks_all() {
        taskService.addTask("Task One");
        taskService.addTask("Task Two");
        taskService.addTask("Task Three");

        List<Task> tasks = taskService.listTasks("ALL");
        assertEquals(3, tasks.size(), "Should return all 3 tasks");
    }

    // -------------------------------------------------------------------------
    // Test 4: Filter pending tasks
    // -------------------------------------------------------------------------
    @Test
    @Order(4)
    @DisplayName("list_tasks: filters PENDING tasks")
    void testListTasks_filterPending() {
        Task t1 = taskService.addTask("Pending Task 1");
        Task t2 = taskService.addTask("Pending Task 2");
        Task t3 = taskService.addTask("Will Complete");
        taskService.completeTask(t3.getId());

        List<Task> pending = taskService.listTasks("PENDING");
        assertEquals(2, pending.size(), "Should return only 2 PENDING tasks");
        assertTrue(pending.stream().allMatch(t -> t.getStatus() == Task.Status.PENDING));
    }

    // -------------------------------------------------------------------------
    // Test 5: Filter completed tasks
    // -------------------------------------------------------------------------
    @Test
    @Order(5)
    @DisplayName("list_tasks: filters COMPLETED tasks")
    void testListTasks_filterCompleted() {
        Task t1 = taskService.addTask("Complete Me 1");
        Task t2 = taskService.addTask("Complete Me 2");
        taskService.addTask("Stays Pending");
        taskService.completeTask(t1.getId());
        taskService.completeTask(t2.getId());

        List<Task> completed = taskService.listTasks("COMPLETED");
        assertEquals(2, completed.size(), "Should return only 2 COMPLETED tasks");
        assertTrue(completed.stream().allMatch(t -> t.getStatus() == Task.Status.COMPLETED));
    }

    // -------------------------------------------------------------------------
    // Test 6: Successful task completion
    // -------------------------------------------------------------------------
    @Test
    @Order(6)
    @DisplayName("complete_task: marks task as COMPLETED")
    void testCompleteTask_success() {
        Task task = taskService.addTask("Review Algorithms");
        assertEquals(Task.Status.PENDING, task.getStatus());

        Task completed = taskService.completeTask(task.getId());
        assertEquals(Task.Status.COMPLETED, completed.getStatus());
        assertNotNull(completed.getCompletedAt());
        assertEquals(task.getId(), completed.getId());
    }

    // -------------------------------------------------------------------------
    // Test 7: Unknown task ID
    // -------------------------------------------------------------------------
    @Test
    @Order(7)
    @DisplayName("complete_task: throws error for unknown ID")
    void testCompleteTask_unknownId_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> taskService.completeTask("task-nonexistent"),
            "Should throw for unknown task ID");
    }

    // -------------------------------------------------------------------------
    // Test 8: Invalid status filter
    // -------------------------------------------------------------------------
    @Test
    @Order(8)
    @DisplayName("list_tasks: rejects invalid status filter")
    void testListTasks_invalidStatus_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> taskService.listTasks("INVALID_STATUS"),
            "Should throw for invalid status filter");
    }

    // -------------------------------------------------------------------------
    // Test 9: Task state persists within session
    // -------------------------------------------------------------------------
    @Test
    @Order(9)
    @DisplayName("state: tasks persist across multiple operations in session")
    void testTaskStatePersistence() {
        Task t1 = taskService.addTask("Study Spring Boot");
        Task t2 = taskService.addTask("Learn MCP Protocol");
        Task t3 = taskService.addTask("Build Demo");

        assertEquals(3, taskService.count());

        taskService.completeTask(t1.getId());

        List<Task> all = taskService.listTasks("ALL");
        assertEquals(3, all.size(), "All 3 tasks should still exist");

        List<Task> pending = taskService.listTasks("PENDING");
        assertEquals(2, pending.size(), "2 tasks should be PENDING");

        List<Task> completed = taskService.listTasks("COMPLETED");
        assertEquals(1, completed.size(), "1 task should be COMPLETED");

        // Verify data integrity
        Task retrieved = taskService.findById(t1.getId()).orElseThrow();
        assertEquals(Task.Status.COMPLETED, retrieved.getStatus());
    }

    // -------------------------------------------------------------------------
    // Test 10: list_tasks returns empty list when no tasks exist
    // -------------------------------------------------------------------------
    @Test
    @Order(10)
    @DisplayName("list_tasks: returns empty list when no tasks match")
    void testListTasks_emptyResult() {
        List<Task> tasks = taskService.listTasks("ALL");
        assertTrue(tasks.isEmpty(), "Should return empty list when no tasks exist");

        taskService.addTask("One Pending Task");
        List<Task> completed = taskService.listTasks("COMPLETED");
        assertTrue(completed.isEmpty(), "Should return empty list when no completed tasks");
    }

    @Test
    @Order(11)
    @DisplayName("counts: pending and completed stay consistent after mutations")
    void testCounts() {
        Task t1 = taskService.addTask("One");
        taskService.addTask("Two");
        assertEquals(2, taskService.countByStatus(Task.Status.PENDING));
        assertEquals(0, taskService.countByStatus(Task.Status.COMPLETED));
        taskService.completeTask(t1.getId());
        assertEquals(1, taskService.countByStatus(Task.Status.PENDING));
        assertEquals(1, taskService.countByStatus(Task.Status.COMPLETED));
    }
}
