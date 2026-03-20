package uk.l3si.eclipse.mcp.tools;

import com.google.gson.JsonObject;
import uk.l3si.eclipse.mcp.bpmn2.tools.*;
import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.tools.*;
import uk.l3si.eclipse.mcp.core.tools.*;

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

    private final DebugContext debugContext;

    public ToolRegistry() {
        // Launch modes
        launchModes.put("run", "Normal test execution (default)");
        launchModes.put("coverage", "Run with EclEmma/JaCoCo code coverage — use 'get_coverage' afterwards for detailed results");
        launchModes.put("debug", "Launch with debugger attached — set breakpoints first, then use debug tools to inspect suspended state");

        // Debugging tools — create early so RunTestTool can use it
        debugContext = new DebugContext();

        // Core tools
        addTool(new ListProjectsTool());
        addTool(new ListLaunchConfigsTool());
        addTool(new ListLaunchesTool());
        addTool(new TerminateTool());
        addTool(new RunTestTool(launchModes, debugContext));
        addTool(new GetTestResultsTool());
        addTool(new GetProblemsTool());
        addTool(new GetConsoleOutputTool());
        addTool(new GetCoverageTool());
        addTool(new FindReferencesTool());
        addTool(new CleanBuildTool());
        BreakpointManager breakpointManager = new BreakpointManager();

        addTool(new BreakpointTool(breakpointManager), "Debugging");
        addTool(new GetDebugStateTool(debugContext), "Debugging");
        addTool(new GetStackTraceTool(debugContext), "Debugging");
        addTool(new ListVariablesTool(debugContext), "Debugging");
        addTool(new EvaluateExpressionTool(debugContext), "Debugging");
        addTool(new StepTool(debugContext), "Debugging");

        // BPMN2 tools (disabled by default)
        addTool(new GetProcessTool(), "BPMN2", false);
        addTool(new CreateProcessTool(), "BPMN2", false);
        addTool(new AddServiceTaskTool(), "BPMN2", false);
        addTool(new AddSubflowCallTool(), "BPMN2", false);
        addTool(new AddScriptTaskTool(), "BPMN2", false);
        addTool(new AddGatewayTool(), "BPMN2", false);
        addTool(new NodeTool(), "BPMN2", false);
        addTool(new FlowTool(), "BPMN2", false);
        addTool(new VariableTool(), "BPMN2", false);
        addTool(new SignalTool(), "BPMN2", false);
        addTool(new ImportTool(), "BPMN2", false);
        addTool(new ItemDefinitionTool(), "BPMN2", false);
        addTool(new TextAnnotationTool(), "BPMN2", false);
        addTool(new AutoLayoutTool(), "BPMN2", false);
    }

    public DebugContext getDebugContext() {
        return debugContext;
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
        return tool.execute(new Args(arguments), message -> {});
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
