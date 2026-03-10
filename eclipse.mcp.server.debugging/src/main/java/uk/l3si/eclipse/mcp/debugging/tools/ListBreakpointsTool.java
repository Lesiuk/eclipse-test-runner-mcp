package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;

public class ListBreakpointsTool implements IMcpTool {

    private final BreakpointManager breakpointManager;

    public ListBreakpointsTool(BreakpointManager breakpointManager) {
        this.breakpointManager = breakpointManager;
    }

    @Override
    public String getName() {
        return "list_breakpoints";
    }

    @Override
    public String getDescription() {
        return "List all breakpoints with their IDs, locations, conditions, and enabled state.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder().build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        return breakpointManager.listBreakpoints();
    }
}
