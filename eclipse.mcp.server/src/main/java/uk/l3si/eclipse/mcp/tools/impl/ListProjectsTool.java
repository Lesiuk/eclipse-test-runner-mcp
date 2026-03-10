package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ListProjectsTool implements IMcpTool {

    @Override
    public String getName() {
        return "list_projects";
    }

    @Override
    public String getDescription() {
        return "List all open projects in the Eclipse workspace";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder().build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        List<String> openProjects = Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects())
                .filter(IProject::isOpen)
                .map(IProject::getName)
                .toList();

        return Map.of("projects", openProjects);
    }
}
