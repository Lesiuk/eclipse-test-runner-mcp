package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class RemoveBreakpointTool implements McpTool {

    private final BreakpointManager breakpointManager;

    public RemoveBreakpointTool(BreakpointManager breakpointManager) {
        this.breakpointManager = breakpointManager;
    }

    @Override
    public String getName() {
        return "remove_breakpoint";
    }

    @Override
    public String getDescription() {
        return "Remove a breakpoint by its ID. Use 'list_breakpoints' to see all breakpoints and their IDs.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("id", PropertySchema.builder().type("integer").description("Breakpoint ID (from set_breakpoint or list_breakpoints)").build())
                .required(List.of("id"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        Long id = args.getLong("id");
        if (id == null) {
            throw new IllegalArgumentException("Missing required parameter: 'id' (breakpoint ID from set_breakpoint or list_breakpoints).");
        }
        return breakpointManager.removeBreakpoint(id);
    }
}
