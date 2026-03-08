package eclipse.mcp.tools;

import com.google.gson.JsonObject;
import eclipse.mcp.tools.impl.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolRegistry {

    private final LinkedHashMap<String, IMcpTool> toolsByName = new LinkedHashMap<>();

    public ToolRegistry() {
        addTool(new ListProjectsTool());
        addTool(new ListLaunchConfigsTool());
        addTool(new ListLaunchesTool());
        addTool(new TerminateTool());
        addTool(new RunTestTool());
        addTool(new LaunchTestTool());
        addTool(new GetTestResultsTool());
        addTool(new GetProblemsTool());
        addTool(new GetFailureTraceTool());
        addTool(new GetConsoleOutputTool());
    }

    private void addTool(IMcpTool tool) {
        toolsByName.put(tool.getName(), tool);
    }

    public List<Map<String, Object>> listToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (IMcpTool tool : toolsByName.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.getName());
            entry.put("description", tool.getDescription());
            entry.put("inputSchema", tool.getInputSchema());
            schemas.add(entry);
        }
        return schemas;
    }

    public Object callTool(String name, JsonObject arguments) throws Exception {
        IMcpTool tool = toolsByName.get(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown tool: " + name);
        }
        return tool.execute(new Args(arguments));
    }
}
