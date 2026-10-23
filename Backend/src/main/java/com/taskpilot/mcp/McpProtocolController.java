package com.taskpilot.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal stateful MCP 2025-11-25 Streamable HTTP server.
 *
 * It accepts JSON-RPC over POST /mcp and uses MCP-Session-Id after the
 * initialize handshake. Tool dispatch is deliberately restricted to the three
 * registered TaskPilot tools.
 */
@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "${taskpilot.cors.allowed-origins:http://localhost:5173}")
public class McpProtocolController {

    public static final String PROTOCOL_VERSION = "2025-11-25";
    public static final String SESSION_HEADER = "MCP-Session-Id";
    public static final String VERSION_HEADER = "MCP-Protocol-Version";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final ObjectMapper objectMapper;
    private final TaskMcpTools taskTools;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public McpProtocolController(ObjectMapper objectMapper, TaskMcpTools taskTools) {
        this.objectMapper = objectMapper;
        this.taskTools = taskTools;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handle(
            @RequestBody JsonNode request,
            @RequestHeader(value = SESSION_HEADER, required = false) String sessionId,
            @RequestHeader(value = VERSION_HEADER, required = false) String protocolVersion) {
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()) {
            return json(error(request.path("id"), -32600, "Invalid JSON-RPC request."));
        }

        String method = request.path("method").asText();
        JsonNode id = request.get("id");
        JsonNode params = request.path("params");

        if (McpSchema.METHOD_INITIALIZE.equals(method)) {
            return initialize(id, params);
        }

        if (!validSession(sessionId) || !PROTOCOL_VERSION.equals(protocolVersion)) {
            return json(error(id, -32000, "Initialize first with MCP 2025-11-25."));
        }

        if (McpSchema.METHOD_NOTIFICATION_INITIALIZED.equals(method)) {
            return ResponseEntity.accepted().build();
        }
        if (McpSchema.METHOD_PING.equals(method)) {
            return json(result(id, objectMapper.createObjectNode()));
        }
        if (McpSchema.METHOD_TOOLS_LIST.equals(method)) {
            return json(result(id, toolsList()));
        }
        if (McpSchema.METHOD_TOOLS_CALL.equals(method)) {
            return json(callTool(id, params));
        }
        return json(error(id, -32601, "Method not found: " + method));
    }

    private ResponseEntity<?> initialize(JsonNode id, JsonNode params) {
        if (!params.isObject() || !PROTOCOL_VERSION.equals(params.path("protocolVersion").asText())) {
            return json(error(id, -32602, "Supported protocol version is " + PROTOCOL_VERSION + "."));
        }
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, Instant.now());

        ObjectNode capabilities = objectMapper.createObjectNode();
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", "taskpilot-mcp-server");
        serverInfo.put("version", "1.0.0");
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("protocolVersion", PROTOCOL_VERSION);
        payload.set("capabilities", capabilities);
        payload.set("serverInfo", serverInfo);
        payload.put("instructions", "Use only add_task, list_tasks, and complete_task for TaskPilot study tasks.");

        return ResponseEntity.ok()
                .header(SESSION_HEADER, sessionId)
                .header(VERSION_HEADER, PROTOCOL_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .body(result(id, payload));
    }

    private ObjectNode toolsList() {
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode tools = response.putArray("tools");
        tools.add(tool("add_task", "Create a study task with a non-empty title.",
                "title", true, null));
        tools.add(tool("list_tasks", "List study tasks, optionally filtered by status.",
                "status", false, new String[] { "ALL", "PENDING", "COMPLETED" }));
        tools.add(tool("complete_task", "Mark an existing study task as completed.",
                "task_id", true, null));
        return response;
    }

    private ObjectNode tool(String name, String description, String argument, boolean required, String[] values) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        schema.putObject("properties").putObject(argument).put("type", "string");
        if (values != null) {
            ArrayNode enumValues = schema.path("properties").path(argument).withArray("enum");
            for (String value : values) enumValues.add(value);
        }
        if (required) schema.putArray("required").add(argument);
        schema.put("additionalProperties", false);
        return tool;
    }

    private ObjectNode callTool(JsonNode id, JsonNode params) {
        if (!params.isObject() || !params.path("name").isTextual()) {
            return error(id, -32602, "tools/call requires a tool name.");
        }
        JsonNode arguments = params.path("arguments");
        if (!arguments.isObject()) {
            return error(id, -32602, "tools/call arguments must be an object.");
        }
        String name = params.path("name").asText();
        String toolResult;
        switch (name) {
            case "add_task" -> {
                if (!arguments.path("title").isTextual() || arguments.path("title").asText().isBlank()) {
                    return error(id, -32602, "add_task requires a non-empty title.");
                }
                toolResult = taskTools.add_task(arguments.path("title").asText());
            }
            case "list_tasks" -> {
                String status = arguments.has("status") ? arguments.path("status").asText() : "ALL";
                if (!("ALL".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)
                        || "COMPLETED".equalsIgnoreCase(status))) {
                    return error(id, -32602, "status must be ALL, PENDING, or COMPLETED.");
                }
                toolResult = taskTools.list_tasks(status);
            }
            case "complete_task" -> {
                if (!arguments.path("task_id").isTextual() || arguments.path("task_id").asText().isBlank()) {
                    return error(id, -32602, "complete_task requires a non-empty task_id.");
                }
                toolResult = taskTools.complete_task(arguments.path("task_id").asText());
            }
            default -> {
                return toolResult(id, "Unknown tool: " + name, true);
            }
        }
        boolean failed = false;
        try { failed = !objectMapper.readTree(toolResult).path("success").asBoolean(); }
        catch (Exception ignored) { failed = true; }
        return toolResult(id, toolResult, failed);
    }

    private ObjectNode toolResult(JsonNode id, String content, boolean isError) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.putArray("content").addObject().put("type", "text").put("text", content);
        if (isError) payload.put("isError", true);
        return result(id, payload);
    }

    private ObjectNode result(JsonNode id, JsonNode payload) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? objectMapper.nullNode() : id);
        response.set("result", payload);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? objectMapper.nullNode() : id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }

    private ResponseEntity<ObjectNode> json(ObjectNode response) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }

    private boolean validSession(String sessionId) {
        if (sessionId == null) return false;
        Instant created = sessions.get(sessionId);
        if (created == null) return false;
        if (created.plus(SESSION_TTL).isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }
}
