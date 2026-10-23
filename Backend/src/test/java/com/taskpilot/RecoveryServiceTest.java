package com.taskpilot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.taskpilot.model.Task;
import com.taskpilot.service.RecoveryService;
import com.taskpilot.service.TaskService;

@SpringBootTest
class RecoveryServiceTest {

    @Autowired
    private RecoveryService recoveryService;

    @Autowired
    private TaskService taskService;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @BeforeEach
    void setUp() {
        taskService.clearAll();
    }

    @Test
    void testGenerateRecoveryWithNoPendingTasks() {
        // Add a completed task
        Task task = taskService.addTask("Completed task");
        taskService.completeTask(task.getId());

        // Generate recovery
        var response = recoveryService.generateRecovery("Help me recover");

        assertNotNull(response);
        assertTrue(response.getRecommendedTasks().isEmpty());
        assertTrue(response.getDeferredTasks().isEmpty());
        assertTrue(response.getExplanation().contains("no pending tasks"));
    }

    @Test
    void testGenerateRecoveryWithPendingTasks() {
        // Skip if no API key
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        // Add pending tasks
        taskService.addTask("Study Java DSA");
        taskService.addTask("Binary search practice");
        taskService.addTask("Java revision");

        // Generate recovery
        var response = recoveryService.generateRecovery("I missed two days of DSA practice");

        assertNotNull(response);
        assertNotNull(response.getExplanation());
        assertNotNull(response.getRecommendedTasks());
        assertNotNull(response.getDeferredTasks());
    }

    @Test
    void testGenerateRecoveryWithEmptyMessage() {
        assertThrows(IllegalArgumentException.class, () -> {
            recoveryService.generateRecovery("");
        });
    }

    @Test
    void testGenerateRecoveryWithNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> {
            recoveryService.generateRecovery(null);
        });
    }

    @Test
    void testGenerateRecoveryWithoutApiKey() {
        // This test verifies the service handles missing API key gracefully
        if (apiKey != null && !apiKey.isBlank()) {
            // Skip if API key is actually configured
            return;
        }

        taskService.addTask("Test task");

        assertThrows(RuntimeException.class, () -> {
            recoveryService.generateRecovery("Help me recover");
        });
    }

    @Test
    void testGenerateRecoveryFiltersInvalidTasks() {
        // Skip if no API key
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }

        // Add pending tasks
        taskService.addTask("Study Java DSA");
        taskService.addTask("Binary search practice");

        // Generate recovery
        var response = recoveryService.generateRecovery("Help me prioritize");

        assertNotNull(response);
        // All recommended/deferred tasks should be from the actual pending list
        for (String task : response.getRecommendedTasks()) {
            assertTrue(task.equals("Study Java DSA") || task.equals("Binary search practice"));
        }
    }
}
