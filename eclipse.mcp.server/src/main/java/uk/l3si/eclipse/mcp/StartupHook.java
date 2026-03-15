package uk.l3si.eclipse.mcp;

import org.eclipse.ui.IStartup;
import uk.l3si.eclipse.mcp.bpmn2.tools.*;
import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.tools.*;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

public class StartupHook implements IStartup {

    @Override
    public void earlyStartup() {
        Activator activator = Activator.getInstance();
        activator.initServer();

        ToolRegistry registry = activator.getToolRegistry();
        registerDebugTools(registry);
        registerBpmn2Tools(registry);
        activator.applyDisabledTools();
    }

    private void registerDebugTools(ToolRegistry registry) {
        registry.addLaunchMode("debug",
                "Launch with debugger attached — set breakpoints first, then use debug tools to inspect suspended state");

        DebugContext debugContext = new DebugContext();
        debugContext.register();

        BreakpointManager breakpointManager = new BreakpointManager();

        registry.addTool(new SetBreakpointTool(breakpointManager), "Debugging");
        registry.addTool(new RemoveBreakpointTool(breakpointManager), "Debugging");
        registry.addTool(new ListBreakpointsTool(breakpointManager), "Debugging");

        registry.addTool(new GetDebugStateTool(debugContext), "Debugging");
        registry.addTool(new ListThreadsTool(debugContext), "Debugging");
        registry.addTool(new GetStackTraceTool(debugContext), "Debugging");
        registry.addTool(new ListVariablesTool(debugContext), "Debugging");
        registry.addTool(new EvaluateExpressionTool(debugContext), "Debugging");
        registry.addTool(new StepTool(debugContext), "Debugging");
        registry.addTool(new ResumeTool(debugContext), "Debugging");
    }

    private void registerBpmn2Tools(ToolRegistry registry) {
        registry.addTool(new GetProcessTool(), "BPMN2", false);
        registry.addTool(new CreateProcessTool(), "BPMN2", false);
        registry.addTool(new AddServiceTaskTool(), "BPMN2", false);
        registry.addTool(new AddSubflowCallTool(), "BPMN2", false);
        registry.addTool(new AddScriptTaskTool(), "BPMN2", false);
        registry.addTool(new AddGatewayTool(), "BPMN2", false);
        registry.addTool(new NodeTool(), "BPMN2", false);
        registry.addTool(new FlowTool(), "BPMN2", false);
        registry.addTool(new VariableTool(), "BPMN2", false);
        registry.addTool(new SignalTool(), "BPMN2", false);
        registry.addTool(new ImportTool(), "BPMN2", false);
        registry.addTool(new ItemDefinitionTool(), "BPMN2", false);
        registry.addTool(new TextAnnotationTool(), "BPMN2", false);
        registry.addTool(new AutoLayoutTool(), "BPMN2", false);
    }
}
