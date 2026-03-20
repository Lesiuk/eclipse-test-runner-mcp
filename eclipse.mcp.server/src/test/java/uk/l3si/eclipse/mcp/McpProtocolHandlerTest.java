package uk.l3si.eclipse.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import static org.junit.jupiter.api.Assertions.*;

public class McpProtocolHandlerTest {

    private McpProtocolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new McpProtocolHandler(new ToolRegistry());
    }

    @Test
    void nullMethodReturnsInvalidRequestError() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(resp.has("error"));
        assertEquals(-32600, resp.getAsJsonObject("error").get("code").getAsInt());
        assertTrue(resp.getAsJsonObject("error").get("message").getAsString().contains("missing method"));
    }

    @Test
    void initializeReturnsProtocolInfo() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(resp.has("result"));
        JsonObject result = resp.getAsJsonObject("result");
        assertEquals("2025-03-26", result.get("protocolVersion").getAsString());
        assertTrue(result.has("capabilities"));
        assertTrue(result.has("serverInfo"));
        assertEquals("eclipse-test-runner-mcp", result.getAsJsonObject("serverInfo").get("name").getAsString());
    }

    @Test
    void pingReturnsEmptyResult() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(resp.has("result"));
    }

    @Test
    void unknownMethodReturnsError() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"foo/bar\"}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertTrue(resp.has("error"));
        assertEquals(-32601, resp.getAsJsonObject("error").get("code").getAsInt());
        assertTrue(resp.getAsJsonObject("error").get("message").getAsString().contains("foo/bar"));
    }

    @Test
    void notificationWithNoIdReturnsNull() {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        assertNull(handler.handleMessage(json));
    }

    @Test
    void toolsListReturnsNonEmptyArray() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        JsonObject result = resp.getAsJsonObject("result");
        assertTrue(result.has("tools"));
        assertTrue(result.getAsJsonArray("tools").size() > 0);
    }

    @Test
    void malformedJsonReturnsNull() {
        assertNull(handler.handleMessage("not json"));
    }

    @Test
    void requestIdIsPreserved() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"ping\"}";
        String response = handler.handleMessage(json);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertEquals(42, resp.get("id").getAsInt());
    }

    @Test
    void stringIdIsPreserved() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":\"abc-123\",\"method\":\"ping\"}";
        String response = handler.handleMessage(json);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertEquals("abc-123", resp.get("id").getAsString());
    }

    @Test
    void toolCallWithUnknownToolReturnsError() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"nonexistent_tool\"}}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        JsonObject result = resp.getAsJsonObject("result");
        assertTrue(result.get("isError").getAsBoolean());
    }

    @Test
    void responseAlwaysHasJsonRpc() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
        String response = handler.handleMessage(json);
        JsonObject resp = JsonParser.parseString(response).getAsJsonObject();
        assertEquals("2.0", resp.get("jsonrpc").getAsString());
    }

    @Test
    void toolCallWithProgressTokenWritesSSE() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"list_projects\",\"_meta\":{\"progressToken\":\"tok1\"}}}";

        var out = new java.io.ByteArrayOutputStream();
        handler.handleMessage(json, out);
        String sse = out.toString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(sse.contains("event: message"), "should have SSE event type");
        assertTrue(sse.contains("\"id\":1"), "should contain the response id");
        assertTrue(sse.contains("data: "), "should be SSE formatted");
    }

    @Test
    void toolCallWithProgressTokenAndErrorWritesSSEError() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"nonexistent\",\"_meta\":{\"progressToken\":\"tok2\"}}}";

        var out = new java.io.ByteArrayOutputStream();
        handler.handleMessage(json, out);
        String sse = out.toString(java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(sse.contains("event: message"));
        assertTrue(sse.contains("isError"));
    }

    @Test
    void toolCallWithoutProgressTokenReturnsJson() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"list_projects\"}}";
        String response = handler.handleMessage(json);
        assertNotNull(response);
    }

    @Test
    void nonToolsCallViaSseWritesResponseAsEvent() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}";
        var out = new java.io.ByteArrayOutputStream();
        handler.handleMessage(json, out);
        String sse = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(sse.contains("event: message"));
        assertTrue(sse.contains("\"id\":4"));
    }
}
