package com.taskpilot.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** Client for TaskPilot's local MCP 2025-11-25 Streamable HTTP server. */
@Component
public class McpClientAdapter {

    private static final Logger logger = Logger.getLogger(McpClientAdapter.class.getName());

    @Value("${server.port:8080}")
    private int serverPort;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AtomicLong requestIds = new AtomicLong(1);

    public McpClientAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String executeTool(String toolName, JsonNode toolArgs) {
        try {
            String endpoint = "http://localhost:" + serverPort + "/mcp";
            HttpResponse<String> init = post(endpoint, initializeRequest(), null);
            if (init.statusCode() != 200) return error("MCP initialization failed.");
            JsonNode initBody = objectMapper.readTree(init.body());
            if (initBody.has("error") || !McpProtocolController.PROTOCOL_VERSION.equals(
                    initBody.path("result").path("protocolVersion").asText())) {
                return error("MCP server did not negotiate " + McpProtocolController.PROTOCOL_VERSION + ".");
            }
            String sessionId = init.headers().firstValue(McpProtocolController.SESSION_HEADER).orElse(null);
            if (sessionId == null || sessionId.isBlank()) return error("MCP server did not create a session.");

            post(endpoint, notification(McpSchema.METHOD_NOTIFICATION_INITIALIZED), sessionId);
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", toolArgs == null ? objectMapper.createObjectNode() : toolArgs);
            HttpResponse<String> response = post(endpoint, request(McpSchema.METHOD_TOOLS_CALL, params), sessionId);
            if (response.statusCode() != 200) return error("MCP tool request failed.");
            return parseToolResult(response.body());
        } catch (Exception e) {
            logger.warning("MCP Streamable HTTP request failed (" + e.getClass().getSimpleName() + ")");
            return error("MCP transport error.");
        }
    }

    private HttpResponse<String> post(String endpoint, ObjectNode body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (sessionId != null) {
            builder.header(McpProtocolController.SESSION_HEADER, sessionId)
                    .header(McpProtocolController.VERSION_HEADER, McpProtocolController.PROTOCOL_VERSION);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ObjectNode initializeRequest() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", McpProtocolController.PROTOCOL_VERSION);
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "taskpilot-gemini-client").put("version", "1.0.0");
        return request(McpSchema.METHOD_INITIALIZE, params);
    }

    private ObjectNode notification(String method) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("method", method);
        return body;
    }

    private ObjectNode request(String method, ObjectNode params) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", requestIds.getAndIncrement());
        body.put("method", method);
        body.set("params", params);
        return body;
    }

    private String parseToolResult(String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            if (response.has("error")) return error(response.path("error").path("message").asText("MCP error."));
            JsonNode content = response.path("result").path("content");
            if (content.isArray() && !content.isEmpty()) return content.get(0).path("text").asText(error("Empty MCP tool result."));
        } catch (Exception e) {
            logger.warning("Unable to parse MCP response.");
        }
        return error("Invalid MCP tool response.");
    }

    private String error(String message) {
        try { return objectMapper.writeValueAsString(Map.of("success", false, "error", message)); }
        catch (Exception e) { return "{\"success\":false,\"error\":\"MCP error.\"}"; }
    }
}
