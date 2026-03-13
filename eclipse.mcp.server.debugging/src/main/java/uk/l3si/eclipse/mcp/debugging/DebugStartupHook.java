package uk.l3si.eclipse.mcp.debugging;

import uk.l3si.eclipse.mcp.Activator;
import uk.l3si.eclipse.mcp.debugging.tools.*;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;
import org.eclipse.ui.IStartup;

public class DebugStartupHook implements IStartup {

    @Override
    public void earlyStartup() {
        ToolRegistry registry = Activator.getInstance().getToolRegistry();
        if (registry == null) {
            return;
        }

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
        registry.addTool(new InspectVariableTool(debugContext), "Debugging");
        registry.addTool(new EvaluateExpressionTool(debugContext), "Debugging");
        registry.addTool(new StepTool(debugContext), "Debugging");
        registry.addTool(new ResumeTool(debugContext), "Debugging");
    }
}
