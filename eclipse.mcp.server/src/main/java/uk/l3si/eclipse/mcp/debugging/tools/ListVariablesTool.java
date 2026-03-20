package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.VariableCollector;
import uk.l3si.eclipse.mcp.debugging.model.ListVariablesResult;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.List;

public class ListVariablesTool implements McpTool {

    private final DebugContext debugContext;

    public ListVariablesTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "list_variables";
    }

    @Override
    public String getDescription() {
        return "List all visible variables in the current stack frame with types and values. "
             + "Collections and maps show their contents. Arrays show first elements as a flat list. "
             + "For custom objects, shows field names — use 'evaluate_expression' to inspect them.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("thread_id", PropertySchema.builder().type("integer").description("Thread ID. Defaults to the current suspended thread.").build())
                .property("frame_index", PropertySchema.builder().type("integer").description("Stack frame index (0 = top, from get_stack_trace). Defaults to 0.").build())
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
        Long threadId = args.getLong("thread_id");
        Integer frameIndex = args.getInt("frame_index");

        IJavaThread thread = debugContext.resolveThread(threadId);
        if (!thread.isSuspended()) {
            throw new IllegalStateException(
                    "Thread '" + thread.getName() + "' is not suspended. "
                    + "Variables can only be listed when execution is paused at a breakpoint or after a step.");
        }
        IJavaStackFrame frame = debugContext.resolveFrame(thread, frameIndex);

        List<VariableResult> variables = VariableCollector.collect(frame, debugContext);
        if (variables == null) {
            throw new IllegalStateException(
                    "Stack frame is no longer valid — the thread may have resumed or the program terminated. "
                    + "Use 'get_debug_state' to check the current state before retrying.");
        }

        return ListVariablesResult.builder()
                .frame(frame.getDeclaringTypeName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .variables(variables)
                .build();
    }
}
