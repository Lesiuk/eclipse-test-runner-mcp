package uk.l3si.eclipse.mcp.tools;

import com.google.gson.JsonObject;
import uk.l3si.eclipse.mcp.tools.impl.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToolRegistry {

    private final LinkedHashMap<String, McpTool> toolsByName = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> toolGroups = new LinkedHashMap<>();
    private final LinkedHashMap<String, String> launchModes = new LinkedHashMap<>();
    private final Set<String> disabledTools = new LinkedHashSet<>();
    private final Set<String> defaultDisabledTools = new LinkedHashSet<>();

    public ToolRegistry() {
        launchModes.put("run", "Normal test execution (default)");
        launchModes.put("coverage", "Run with EclEmma/JaCoCo code coverage — use 'get_coverage' afterwards for detailed results");

        addTool(new ListProjectsTool());
        addTool(new ListLaunchConfigsTool());
        addTool(new ListLaunchesTool());
        addTool(new TerminateTool());
        addTool(new RunTestTool(launchModes));
        addTool(new GetTestResultsTool());
        addTool(new GetProblemsTool());
        addTool(new GetFailureTraceTool());
        addTool(new GetConsoleOutputTool());
        addTool(new GetCoverageTool());
        addTool(new FindReferencesTool());
    }

    public synchronized void addTool(McpTool tool) {
        addTool(tool, "Core");
    }

    public synchronized void addTool(McpTool tool, String group) {
        addTool(tool, group, true);
    }

    public synchronized void addTool(McpTool tool, String group, boolean enabledByDefault) {
        toolsByName.put(tool.getName(), tool);
        toolGroups.put(tool.getName(), group);
        if (!enabledByDefault) {
            defaultDisabledTools.add(tool.getName());
        }
    }

    public synchronized void addLaunchMode(String name, String description) {
        launchModes.put(name, description);
    }

    public synchronized Map<String, String> getLaunchModes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(launchModes));
    }

    public synchronized List<Map<String, Object>> listToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (McpTool tool : toolsByName.values()) {
            if (disabledTools.contains(tool.getName())) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.getName());
            entry.put("description", tool.getDescription());
            entry.put("inputSchema", tool.getInputSchema());
            schemas.add(entry);
        }
        return schemas;
    }

    public Object callTool(String name, JsonObject arguments) throws Exception {
        McpTool tool;
        synchronized (this) {
            tool = toolsByName.get(name);
            if (tool == null || disabledTools.contains(name)) {
                throw new IllegalArgumentException("Unknown tool: " + name);
            }
            validateParameters(name, tool, arguments);
        }
        return tool.execute(new Args(arguments));
    }

    public synchronized Map<String, List<McpTool>> getToolsByGroup() {
        Map<String, List<McpTool>> result = new LinkedHashMap<>();
        for (Map.Entry<String, McpTool> entry : toolsByName.entrySet()) {
            String group = toolGroups.getOrDefault(entry.getKey(), "Core");
            result.computeIfAbsent(group, k -> new ArrayList<>()).add(entry.getValue());
        }
        return result;
    }

    public synchronized Set<String> getDisabledTools() {
        return new LinkedHashSet<>(disabledTools);
    }

    public synchronized void setDisabledTools(Set<String> disabled) {
        disabledTools.clear();
        disabledTools.addAll(disabled);
    }

    public synchronized boolean isToolEnabled(String toolName) {
        return !disabledTools.contains(toolName);
    }

    public synchronized Set<String> getDefaultDisabledTools() {
        return new LinkedHashSet<>(defaultDisabledTools);
    }

    private void validateParameters(String toolName, McpTool tool, JsonObject arguments) {
        if (arguments == null || arguments.isEmpty()) return;
        Set<String> known = tool.getInputSchema().getPropertyNames();
        Set<String> unknown = new LinkedHashSet<>();
        for (String key : arguments.keySet()) {
            if (!known.contains(key)) {
                unknown.add(key);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown parameter(s) for '" + toolName + "': " + unknown
                    + ". Valid parameters: " + known);
        }
    }
}
