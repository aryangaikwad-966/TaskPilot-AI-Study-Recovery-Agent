package com.taskpilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskpilot.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises MCP 2025-11-25 through its actual Streamable HTTP JSON-RPC endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class McpIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TaskService taskService;

    @BeforeEach void clear() { taskService.clearAll(); }

    @Test
    void initializesListsAndExecutesToolsWithSharedState() throws Exception {
        String session = initialize();
        JsonNode listed = call(session, 2, "tools/list", "{}");
        assertEquals(3, listed.path("result").path("tools").size());

        JsonNode added = call(session, 3, "tools/call", "{\"name\":\"add_task\",\"arguments\":{\"title\":\"MCP study task\"}}");
        JsonNode addPayload = objectMapper.readTree(added.path("result").path("content").get(0).path("text").asText());
        String id = addPayload.path("task_id").asText();
        assertEquals("PENDING", taskService.findById(id).orElseThrow().getStatus().name());

        JsonNode listedViaRest = objectMapper.readTree(
                mockMvc.perform(get("/api/tasks")).andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString());
        assertEquals(1, listedViaRest.path("pendingCount").asInt());

        JsonNode completed = call(session, 4, "tools/call", "{\"name\":\"complete_task\",\"arguments\":{\"task_id\":\"" + id + "\"}}");
        assertFalse(completed.path("result").path("isError").asBoolean());
        assertEquals("COMPLETED", taskService.findById(id).orElseThrow().getStatus().name());
    }

    @Test
    void getMcpDoesNotAdvertiseLegacySse() throws Exception {
        mockMvc.perform(get("/mcp")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void rejectsUnsupportedVersionInvalidArgumentsAndUnknownMethods() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        JsonNode unsupported = response(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(body));
        assertEquals(-32602, unsupported.path("error").path("code").asInt());

        String session = initialize();
        JsonNode badArguments = call(session, 2, "tools/call", "{\"name\":\"add_task\",\"arguments\":{}}");
        assertEquals(-32602, badArguments.path("error").path("code").asInt());
        JsonNode unknown = call(session, 3, "no/such/method", "{}");
        assertEquals(-32601, unknown.path("error").path("code").asInt());
    }

    private String initialize() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";
        var result = mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("2025-11-25", response.path("result").path("protocolVersion").asText());
        String session = result.getResponse().getHeader("MCP-Session-Id");
        assertNotNull(session);
        return session;
    }

    private JsonNode call(String session, int id, String method, String params) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}";
        return response(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Session-Id", session).header("MCP-Protocol-Version", "2025-11-25").content(body));
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
