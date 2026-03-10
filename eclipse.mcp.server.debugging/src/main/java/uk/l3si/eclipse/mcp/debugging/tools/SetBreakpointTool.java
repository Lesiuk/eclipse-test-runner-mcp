package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class SetBreakpointTool implements IMcpTool {

    private final BreakpointManager breakpointManager;

    public SetBreakpointTool(BreakpointManager breakpointManager) {
        this.breakpointManager = breakpointManager;
    }

    @Override
    public String getName() {
        return "set_breakpoint";
    }

    @Override
    public String getDescription() {
        return "Set a line breakpoint at a specific class and line number. "
             + "Optionally set a condition (a Java boolean expression) — the debugger will only suspend when the condition is true. "
             + "Returns the breakpoint ID which can be used to remove it later.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("class", PropertySchema.string("Fully qualified class name (e.g. 'com.example.MyService')"))
                .property("line", PropertySchema.builder().type("integer").description("Line number (1-based)").build())
                .property("condition", PropertySchema.string("Optional Java boolean expression — breakpoint only triggers when this evaluates to true"))
                .required(List.of("class", "line"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String className = args.requireString("class", "fully qualified class name");
        if (className.isBlank()) {
            throw new IllegalArgumentException("'class' must be a fully qualified class name (e.g. 'com.example.MyService'), got blank value.");
        }
        int line = args.requireInt("line", "line number");
        if (line <= 0) {
            throw new IllegalArgumentException("'line' must be a positive number, got " + line + ".");
        }
        String condition = args.getString("condition");
        return breakpointManager.setBreakpoint(className, line, condition);
    }
}
