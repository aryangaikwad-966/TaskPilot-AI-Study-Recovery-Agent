package com.taskpilot.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskpilot.dto.RecoveryResponse;
import com.taskpilot.model.Task;

/**
 * RecoveryService generates study recovery proposals using Gemini.
 * It takes the user's recovery request and current task context,
 * then returns a structured recovery plan for user approval.
 */
@Service
public class RecoveryService {

    private static final Logger logger = Logger.getLogger(RecoveryService.class.getName());

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String geminiModel;

    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RecoveryService(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Generates a recovery proposal based on the user's request and current tasks.
     *
     * @param userMessage The user's recovery request
     * @return A structured recovery proposal
     */
    public RecoveryResponse generateRecovery(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("Recovery message must not be empty.");
        }

        try {
            // Get current task context
            List<Task> allTasks = taskService.listTasks("ALL");
            List<Task> pendingTasks = taskService.listTasks("PENDING");

            if (pendingTasks.isEmpty()) {
                return new RecoveryResponse(
                    "You have no pending tasks, so no recovery plan is needed. Great job staying on track!",
                    List.of(),
                    List.of(),
                    List.of("Based on current task state, no pending tasks exist.")
                );
            }

            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Gemini API key not configured.");
            }

            // Build prompt with task context
            String prompt = buildRecoveryPrompt(userMessage, pendingTasks, allTasks);

            // Call Gemini API
            String requestBody = buildGeminiRequest(prompt);
            String geminiResponse = callGeminiApi(requestBody);

            if (geminiResponse == null) {
                throw new RuntimeException("No response from Gemini API.");
            }

            // Parse and validate response
            return parseRecoveryResponse(geminiResponse, pendingTasks);

        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            logger.severe("RecoveryService request failed (" + e.getClass().getSimpleName() + ")");
            throw new RuntimeException("Failed to generate recovery plan.");
        }
    }

    private String buildRecoveryPrompt(String userMessage, List<Task> pendingTasks, List<Task> allTasks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are TaskPilot, an AI study recovery assistant. ");
        prompt.append("Your goal is to help students recover when their study schedule falls behind.\n\n");

        prompt.append("User's recovery request: \"").append(userMessage).append("\"\n\n");

        prompt.append("Current pending tasks:\n");
        for (Task task : pendingTasks) {
            prompt.append("- ").append(task.getTitle()).append(" (ID: ").append(task.getId()).append(")\n");
        }

        prompt.append("\nTotal tasks: ").append(allTasks.size())
              .append(" (Pending: ").append(pendingTasks.size())
              .append(", Completed: ").append(allTasks.size() - pendingTasks.size()).append(")\n\n");

        prompt.append("Generate a recovery proposal in JSON format with this exact structure:\n");
        prompt.append("{\n");
        prompt.append("  \"explanation\": \"Brief explanation of the recovery strategy (2-3 sentences)\",\n");
        prompt.append("  \"recommendedTasks\": [\"task titles to prioritize today\"],\n");
        prompt.append("  \"deferredTasks\": [\"task titles that can wait\"],\n");
        prompt.append("  \"assumptions\": [\"any assumptions made due to missing information\"]\n");
        prompt.append("}\n\n");

        prompt.append("Guidelines:\n");
        prompt.append("- Recommended tasks should be a realistic subset (2-4 tasks max)\n");
        prompt.append("- Use exact task titles from the list above\n");
        prompt.append("- Be conservative in recommendations to avoid overwhelming the user\n");
        prompt.append("- If no tasks can reasonably be deferred, return empty arrays\n");
        prompt.append("- Return ONLY valid JSON, no additional text\n");

        return prompt.toString();
    }

    private String buildGeminiRequest(String prompt) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();

        ArrayNode contents = request.putArray("contents");
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode generationConfig = request.putObject("generationConfig");
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 1024);

        return objectMapper.writeValueAsString(request);
    }

    private String callGeminiApi(String requestBody) throws Exception {
        String url = GEMINI_API_BASE + geminiModel + ":generateContent";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warning("Gemini API returned HTTP " + response.statusCode());
            throw new RuntimeException("Gemini API returned HTTP " + response.statusCode());
        }

        return response.body();
    }

    private RecoveryResponse parseRecoveryResponse(String geminiResponse, List<Task> pendingTasks) throws Exception {
        JsonNode responseNode = objectMapper.readTree(geminiResponse);

        // Extract text from Gemini response
        String text = extractText(responseNode);
        if (text == null || text.isBlank()) {
            throw new RuntimeException("No text in Gemini response");
        }

        JsonNode proposalNode;
        try {
            proposalNode = objectMapper.readTree(unwrapJson(text));
        } catch (Exception e) {
            logger.warning("Gemini did not return valid JSON, using fallback");
            return createFallbackResponse(text, pendingTasks);
        }

        // Extract fields with validation
        String explanation = proposalNode.has("explanation")
            ? proposalNode.get("explanation").asText()
            : "Based on your request, here's a recovery plan.";

        List<String> recommendedTasks = extractStringArray(proposalNode, "recommendedTasks");
        List<String> deferredTasks = extractStringArray(proposalNode, "deferredTasks");
        List<String> assumptions = extractStringArray(proposalNode, "assumptions");

        // Validate that recommended/deferred tasks are actually in the pending list
        List<String> pendingTitles = pendingTasks.stream()
            .map(Task::getTitle)
            .collect(Collectors.toList());

        recommendedTasks = recommendedTasks.stream()
            .filter(pendingTitles::contains)
            .collect(Collectors.toList());

        deferredTasks = deferredTasks.stream()
            .filter(pendingTitles::contains)
            .collect(Collectors.toList());

        return new RecoveryResponse(explanation, recommendedTasks, deferredTasks, assumptions);
    }

    private String extractText(JsonNode responseNode) {
        try {
            JsonNode textNode = responseNode
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text");
            if (textNode != null && !textNode.isMissingNode() && !textNode.isNull()) {
                return textNode.asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<String> extractStringArray(JsonNode node, String fieldName) {
        List<String> result = new ArrayList<>();
        if (node.has(fieldName) && node.get(fieldName).isArray()) {
            ArrayNode array = (ArrayNode) node.get(fieldName);
            for (JsonNode item : array) {
                if (item.isTextual() && !item.asText().isBlank()) {
                    result.add(item.asText());
                }
            }
        }
        return result;
    }

    private String unwrapJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            int fence = trimmed.lastIndexOf("```");
            if (newline >= 0 && fence > newline) {
                trimmed = trimmed.substring(newline + 1, fence).trim();
            }
        }
        return trimmed;
    }

    private RecoveryResponse createFallbackResponse(String rawText, List<Task> pendingTasks) {
        List<String> pendingTitles = pendingTasks.stream()
            .map(Task::getTitle)
            .collect(Collectors.toList());

        // Conservative fallback: recommend first 2 tasks
        List<String> recommended = pendingTitles.stream()
            .limit(2)
            .collect(Collectors.toList());

        List<String> deferred = pendingTitles.stream()
            .skip(2)
            .collect(Collectors.toList());

        return new RecoveryResponse(
            "Here's a recovery plan based on your current tasks. I've prioritized the first few items.",
            recommended,
            deferred,
            List.of("Due to a parsing issue, this is a conservative fallback recommendation.")
        );
    }
}
