package com.taskpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.taskpilot.mcp.McpClientAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * GeminiService handles natural-language requests via the Gemini API.
 *
 * Flow:
 *   1. Send user message + tool declarations to Gemini
 *   2. Receive function call suggestion from Gemini
 *   3. Validate the tool name
 *   4. Route to McpClientAdapter which calls the actual MCP server
 *   5. Return the tool result to Gemini for a final human-readable reply
 *   6. Return final reply to the user
 *
 * Uses raw HTTP (java.net.http) to call the Gemini REST API so that
 * no additional cloud SDKs are required and costs remain transparent.
 */
@Service
public class GeminiService implements ChatService {

    private static final Logger logger = Logger.getLogger(GeminiService.class.getName());

    private static final String GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash}")
    private String geminiModel;

    private final McpClientAdapter mcpClientAdapter;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Tool definitions sent to Gemini as function declarations
    private static final String TOOL_DECLARATIONS = """
        {
          "tools": [{
            "function_declarations": [
              {
                "name": "list_tasks",
                "description": "List study tasks. Filter by status: ALL, PENDING, or COMPLETED.",
                "parameters": {
                  "type": "object",
                  "properties": {
                    "status": {
                      "type": "string",
                      "description": "Filter tasks by status. Use ALL, PENDING, or COMPLETED.",
                      "enum": ["ALL", "PENDING", "COMPLETED"]
                    }
                  }
                }
              }
            ]
          }]
        }
        """;

    public GeminiService(McpClientAdapter mcpClientAdapter, ObjectMapper objectMapper) {
        this.mcpClientAdapter = mcpClientAdapter;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Process a user message through Gemini function calling → MCP execution.
     *
     * @param userMessage Natural-language request from the user
     * @return Human-readable reply
     */
    public String chat(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "Please enter a message.";
        }

        // Check API key
        if (apiKey == null || apiKey.isBlank()) {
            return "⚠️ Gemini API key not configured. Set GEMINI_API_KEY and restart the server.\n" +
                   "You can still use MCP tools directly at POST /mcp.";
        }

        try {
            // --- Step 1: Call Gemini with tool declarations ---
            String requestBody = buildGeminiRequest(userMessage);
            String geminiResponse = callGeminiApi(requestBody);

            if (geminiResponse == null) {
                return "Error: No response from Gemini API.";
            }

            JsonNode responseNode = objectMapper.readTree(geminiResponse);

            // --- Step 2: Check for function call ---
            JsonNode functionCall = extractFunctionCall(responseNode);

            if (functionCall != null) {
                String toolName = functionCall.path("name").asText();
                JsonNode toolArgs = functionCall.path("args");

                // --- Step 3: Validate tool name ---
                if (!isValidTool(toolName)) {
                    return "Error: Unknown tool requested by Gemini: " + toolName;
                }

                logger.info("Gemini requested tool: " + toolName + " with args: " + toolArgs);

                // --- Step 4: Execute via MCP (actual protocol call) ---
                String mcpResult = mcpClientAdapter.executeTool(toolName, toolArgs);

                logger.info("MCP result: " + mcpResult);

                // --- Step 5: Send result back to Gemini for final response ---
                String followUpRequest = buildFollowUpRequest(userMessage, toolName, toolArgs, mcpResult);
                String finalGeminiResponse = callGeminiApi(followUpRequest);

                if (finalGeminiResponse == null) {
                    // Return the raw MCP result if Gemini follow-up fails
                    return formatMcpResultAsText(mcpResult);
                }

                String finalText = extractText(objectMapper.readTree(finalGeminiResponse));
                return finalText != null ? finalText : formatMcpResultAsText(mcpResult);

            } else {
                // No tool call — return Gemini's direct text response
                String text = extractText(responseNode);
                if (text != null) {
                    return text;
                }
                return "I can help you manage your study tasks. Try:\n" +
                       "• \"Show all my pending tasks\"\n" +
                       "• Use the dashboard to add or complete a task\"";
            }

        } catch (Exception e) {
            logger.severe("GeminiService request failed (" + e.getClass().getSimpleName() + ")");
            return "I couldn't complete that request right now. Please try again.";
        }
    }

    private String buildGeminiRequest(String userMessage) throws Exception {
        JsonNode toolsNode = objectMapper.readTree(TOOL_DECLARATIONS);
        ObjectNode request = objectMapper.createObjectNode();

        ArrayNode contents = request.putArray("contents");
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        ArrayNode parts = userContent.putArray("parts");
        parts.addObject().put("text", userMessage);

        request.set("tools", toolsNode.get("tools"));

        ObjectNode toolConfig = request.putObject("tool_config");
        toolConfig.putObject("function_calling_config").put("mode", "AUTO");

        return objectMapper.writeValueAsString(request);
    }

    private String buildFollowUpRequest(String originalMessage, String toolName,
                                         JsonNode toolArgs, String toolResult) throws Exception {
        JsonNode toolsNode = objectMapper.readTree(TOOL_DECLARATIONS);
        ObjectNode request = objectMapper.createObjectNode();

        ArrayNode contents = request.putArray("contents");

        // Original user message
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        userContent.putArray("parts").addObject().put("text", originalMessage);

        // Model's function call
        ObjectNode modelContent = contents.addObject();
        modelContent.put("role", "model");
        ArrayNode modelParts = modelContent.putArray("parts");
        ObjectNode funcCallPart = modelParts.addObject();
        ObjectNode funcCall = funcCallPart.putObject("function_call");
        funcCall.put("name", toolName);
        funcCall.set("args", toolArgs);

        // Tool result (function response)
        ObjectNode toolContent = contents.addObject();
        toolContent.put("role", "user");
        ArrayNode toolParts = toolContent.putArray("parts");
        ObjectNode funcResp = toolParts.addObject().putObject("function_response");
        funcResp.put("name", toolName);
        funcResp.set("response", objectMapper.readTree(toolResult));

        request.set("tools", toolsNode.get("tools"));

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

    private JsonNode extractFunctionCall(JsonNode responseNode) {
        try {
            return responseNode
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("functionCall");
        } catch (Exception e) {
            return null;
        }
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

    private boolean isValidTool(String toolName) {
        // The conversational assistant may inspect tasks, but it must not
        // mutate task state without the explicit recovery approval workflow.
        return "list_tasks".equals(toolName);
    }

    private String formatMcpResultAsText(String mcpResult) {
        try {
            JsonNode node = objectMapper.readTree(mcpResult);
            if (node.has("message")) return node.get("message").asText();
            if (node.has("error")) return "Error: " + node.get("error").asText();
            if (node.has("tasks")) {
                int count = node.path("count").asInt(0);
                return count == 0 ? "No tasks found." : "Found " + count + " task(s):\n" + mcpResult;
            }
            return mcpResult;
        } catch (Exception e) {
            return mcpResult;
        }
    }
}
