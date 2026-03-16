package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class BreakpointTool implements McpTool {

    private final BreakpointManager breakpointManager;

    public BreakpointTool(BreakpointManager breakpointManager) {
        this.breakpointManager = breakpointManager;
    }

    @Override
    public String getName() {
        return "breakpoint";
    }

    @Override
    public String getDescription() {
        return "Manage breakpoints. "
             + "action='set': set a line breakpoint (requires class, line; optional condition). "
             + "action='remove': remove by ID. "
             + "action='list': list all breakpoints with IDs, locations, and conditions.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("action", PropertySchema.stringEnum(
                        "Breakpoint action: 'set', 'remove', or 'list'.",
                        List.of("set", "remove", "list")))
                .property("class", PropertySchema.string("Fully qualified class name (e.g. 'com.example.MyService'). Required for 'set'."))
                .property("line", PropertySchema.builder().type("integer").description("Line number (1-based). Required for 'set'.").build())
                .property("condition", PropertySchema.string("Optional Java boolean expression — breakpoint only triggers when this evaluates to true. For 'set' only."))
                .property("id", PropertySchema.builder().type("integer").description("Breakpoint ID. Required for 'remove'.").build())
                .required(List.of("action"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "Breakpoint action: set, remove, or list");

        return switch (action) {
            case "set" -> executeSet(args);
            case "remove" -> executeRemove(args);
            case "list" -> breakpointManager.listBreakpoints();
            default -> throw new IllegalArgumentException(
                    "Invalid breakpoint action: '" + action + "'. Must be 'set', 'remove', or 'list'.");
        };
    }

    private Object executeSet(Args args) throws Exception {
        String className = args.requireString("class", "fully qualified class name");
        if (className.isBlank()) {
            throw new IllegalArgumentException(
                    "'class' must be a fully qualified class name (e.g. 'com.example.MyService'), got blank value.");
        }
        int line = args.requireInt("line", "line number");
        if (line <= 0) {
            throw new IllegalArgumentException("'line' must be a positive number, got " + line + ".");
        }
        String condition = args.getString("condition");
        return breakpointManager.setBreakpoint(className, line, condition);
    }

    private Object executeRemove(Args args) throws Exception {
        Long id = args.getLong("id");
        if (id == null) {
            throw new IllegalArgumentException(
                    "Missing required parameter: 'id' (breakpoint ID from breakpoint action='set' or action='list').");
        }
        return breakpointManager.removeBreakpoint(id);
    }
}
