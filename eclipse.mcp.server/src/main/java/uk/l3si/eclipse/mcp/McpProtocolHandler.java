package uk.l3si.eclipse.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpProtocolHandler {

    private static final String MCP_VERSION = "2024-11-05";
    private static final Gson GSON = new GsonBuilder().create();

    private final ToolRegistry registry;

    public McpProtocolHandler(ToolRegistry registry) {
        this.registry = registry;
    }

    public String handleMessage(String rawJson) {
        try {
            JsonObject msg = JsonParser.parseString(rawJson).getAsJsonObject();

            if (!msg.has("id")) {
                return null;
            }

            JsonElement requestId = msg.get("id");
            String method = msg.has("method") ? msg.get("method").getAsString() : null;

            if (method == null) {
                return errorResponse(requestId, -32600, "Invalid Request: missing method");
            }

            JsonObject params = msg.has("params") ? msg.getAsJsonObject("params") : new JsonObject();
            Object result = dispatch(method, params);
            return successResponse(requestId, result);
        } catch (Exception ex) {
            return handleTopLevelError(rawJson, ex);
        }
    }

    private Object dispatch(String method, JsonObject params) throws Exception {
        return switch (method) {
            case "initialize" -> serverCapabilities();
            case "tools/list" -> Map.of("tools", registry.listToolSchemas());
            case "tools/call" -> executeTool(params);
            case "ping" -> Map.of();
            default -> throw new NoSuchMethodException("Method not found: " + method);
        };
    }

    private Object serverCapabilities() {
        String version = resolveServerVersion();
        return Map.of(
                "protocolVersion", MCP_VERSION,
                "capabilities", Map.of("tools", Map.of()),
                "serverInfo", Map.of("name", "eclipse-test-runner-mcp", "version", version)
        );
    }

    private Object executeTool(JsonObject params) {
        String toolName = params.get("name").getAsString();
        JsonObject toolArgs = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        try {
            Object result = registry.callTool(toolName, toolArgs, message -> {});
            return Map.of(
                    "content", List.of(Map.of("type", "text", "text", GSON.toJson(result)))
            );
        } catch (Exception ex) {
            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("content", List.of(Map.of("type", "text", "text", "Error: " + ex.getMessage())));
            errorResult.put("isError", true);
            return errorResult;
        }
    }

    private String resolveServerVersion() {
        try {
            return org.eclipse.core.runtime.Platform.getBundle(Activator.PLUGIN_ID).getVersion().toString();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String handleTopLevelError(String rawJson, Exception ex) {
        try {
            JsonObject msg = JsonParser.parseString(rawJson).getAsJsonObject();
            if (msg.has("id")) {
                int code = (ex instanceof NoSuchMethodException) ? -32601 : -32603;
                return errorResponse(msg.get("id"), code, ex.getMessage());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String successResponse(JsonElement id, Object result) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id);
        resp.add("result", GSON.toJsonTree(result));
        return GSON.toJson(resp);
    }

    private String errorResponse(JsonElement id, int code, String message) {
        JsonObject resp = new JsonObject();
        resp.addProperty("jsonrpc", "2.0");
        resp.add("id", id);
        resp.add("error", GSON.toJsonTree(Map.of("code", code, "message", message)));
        return GSON.toJson(resp);
    }
}
