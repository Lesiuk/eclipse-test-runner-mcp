package uk.l3si.eclipse.mcp.bpmn2;

import org.eclipse.ui.IStartup;
import uk.l3si.eclipse.mcp.Activator;
import uk.l3si.eclipse.mcp.bpmn2.tools.*;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

public class Bpmn2StartupHook implements IStartup {

    @Override
    public void earlyStartup() {
        ToolRegistry registry = Activator.getInstance().getToolRegistry();
        if (registry == null) {
            return;
        }

        registry.addTool(new GetProcessTool());
        registry.addTool(new CreateProcessTool());
        registry.addTool(new AddServiceTaskTool());
        registry.addTool(new AddSubflowCallTool());
        registry.addTool(new AddScriptTaskTool());
        registry.addTool(new AddExtensionPointTool());
        registry.addTool(new AddGatewayTool());
        registry.addTool(new AddStartEventTool());
        registry.addTool(new AddEndEventTool());
        registry.addTool(new UpdateNodeTool());
        registry.addTool(new RemoveNodeTool());
        registry.addTool(new AddFlowTool());
        registry.addTool(new UpdateFlowTool());
        registry.addTool(new RemoveFlowTool());
        registry.addTool(new AddVariableTool());
        registry.addTool(new RemoveVariableTool());
        registry.addTool(new AddSignalTool());
        registry.addTool(new AutoLayoutTool());
    }
}
