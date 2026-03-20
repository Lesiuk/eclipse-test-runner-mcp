package uk.l3si.eclipse.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpProtocolHandler {

    private static final String MCP_VERSION = "2025-03-26";
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

    public void handleMessage(String rawJson, OutputStream out) throws IOException {
        int[] counter = {0};
        try {
            JsonObject msg = JsonParser.parseString(rawJson).getAsJsonObject();
            if (!msg.has("id")) return;

            JsonElement requestId = msg.get("id");
            String method = msg.has("method") ? msg.get("method").getAsString() : null;
            JsonObject params = msg.has("params") ? msg.getAsJsonObject("params") : new JsonObject();

            if (!"tools/call".equals(method)) {
                counter[0]++;
                Object result = dispatch(method, params);
                writeSseEvent(out, counter[0], successResponse(requestId, result));
                return;
            }

            String progressToken = extractProgressToken(params);

            ProgressReporter reporter = (message) -> {
                try {
                    counter[0]++;
                    writeSseEvent(out, counter[0], progressNotification(progressToken, counter[0], message));
                } catch (IOException e) {
                    // Client disconnected — silently ignore
                }
            };

            Object result = executeTool(params, reporter);
            counter[0]++;
            writeSseEvent(out, counter[0], successResponse(requestId, result));

        } catch (Exception ex) {
            try {
                JsonObject msg = JsonParser.parseString(rawJson).getAsJsonObject();
                if (msg.has("id")) {
                    counter[0]++;
                    int code = (ex instanceof NoSuchMethodException) ? -32601 : -32603;
                    writeSseEvent(out, counter[0], errorResponse(msg.get("id"), code, ex.getMessage()));
                }
            } catch (Exception ignored) {}
        }
    }

    private String extractProgressToken(JsonObject params) {
        if (params.has("_meta")) {
            JsonObject meta = params.getAsJsonObject("_meta");
            if (meta.has("progressToken")) {
                return meta.get("progressToken").getAsString();
            }
        }
        return null;
    }

    private void writeSseEvent(OutputStream out, int id, String json) throws IOException {
        String event = "id: " + id + "\nevent: message\ndata: " + json + "\n\n";
        out.write(event.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String progressNotification(String progressToken, int progress, String message) {
        JsonObject notification = new JsonObject();
        notification.addProperty("jsonrpc", "2.0");
        notification.addProperty("method", "notifications/progress");
        JsonObject params = new JsonObject();
        params.addProperty("progressToken", progressToken);
        params.addProperty("progress", progress);
        params.addProperty("message", message);
        notification.add("params", params);
        return GSON.toJson(notification);
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
        return executeTool(params, message -> {});
    }

    private Object executeTool(JsonObject params, ProgressReporter reporter) {
        String toolName = params.get("name").getAsString();
        JsonObject toolArgs = params.has("arguments") ? params.getAsJsonObject("arguments") : new JsonObject();

        try {
            Object result = registry.callTool(toolName, toolArgs, reporter);
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
