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

        registry.addTool(new GetProcessTool(), "BPMN2");
        registry.addTool(new CreateProcessTool(), "BPMN2");
        registry.addTool(new AddServiceTaskTool(), "BPMN2");
        registry.addTool(new AddSubflowCallTool(), "BPMN2");
        registry.addTool(new AddScriptTaskTool(), "BPMN2");
        registry.addTool(new AddGatewayTool(), "BPMN2");
        registry.addTool(new NodeTool(), "BPMN2");
        registry.addTool(new FlowTool(), "BPMN2");
        registry.addTool(new VariableTool(), "BPMN2");
        registry.addTool(new SignalTool(), "BPMN2");
        registry.addTool(new ImportTool(), "BPMN2");
        registry.addTool(new ItemDefinitionTool(), "BPMN2");
        registry.addTool(new TextAnnotationTool(), "BPMN2");
        registry.addTool(new AutoLayoutTool(), "BPMN2");
    }
}
