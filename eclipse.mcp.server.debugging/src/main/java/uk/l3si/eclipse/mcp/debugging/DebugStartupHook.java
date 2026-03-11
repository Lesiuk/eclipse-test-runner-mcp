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

        registry.addTool(new SetBreakpointTool(breakpointManager));
        registry.addTool(new RemoveBreakpointTool(breakpointManager));
        registry.addTool(new ListBreakpointsTool(breakpointManager));

        registry.addTool(new GetDebugStateTool(debugContext));
        registry.addTool(new ListThreadsTool(debugContext));
        registry.addTool(new GetStackTraceTool(debugContext));
        registry.addTool(new ListVariablesTool(debugContext));
        registry.addTool(new InspectVariableTool(debugContext));
        registry.addTool(new EvaluateExpressionTool(debugContext));
        registry.addTool(new StepTool(debugContext));
        registry.addTool(new ResumeTool(debugContext));
    }
}
