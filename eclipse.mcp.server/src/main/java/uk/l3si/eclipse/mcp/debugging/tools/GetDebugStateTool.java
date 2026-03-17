package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.DebugStateResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

public class GetDebugStateTool implements McpTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final DebugContext debugContext;

    public GetDebugStateTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "get_debug_state";
    }

    @Override
    public String getDescription() {
        return "Check the current debug session state. Returns whether a debug session is active, "
             + "if a thread is suspended (e.g. at a breakpoint), which thread and at what location. "
             + "By default, blocks until a breakpoint is hit or the session ends (wait_for_suspend=true). "
             + "Set wait_for_suspend=false only if you need a non-blocking snapshot.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("wait_for_suspend", PropertySchema.bool(
                        "Whether to block until a thread suspends (e.g. hits a breakpoint) or the session terminates. "
                        + "Defaults to true. Set to false only for non-blocking state checks."))
                .property("timeout", PropertySchema.builder()
                        .type("integer")
                        .description("Timeout in seconds when wait_for_suspend is true (default: "
                                + DEFAULT_TIMEOUT_SECONDS + ").")
                        .build())
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        boolean waitForSuspend = args.getBoolean("wait_for_suspend", true);

        if (waitForSuspend) {
            int timeoutSeconds = args.getInt("timeout") != null
                    ? args.getInt("timeout") : DEFAULT_TIMEOUT_SECONDS;
            debugContext.waitForSuspendOrTerminate(timeoutSeconds);
        }

        IJavaDebugTarget target = debugContext.getCurrentTarget();
        if (target == null || target.isTerminated()) {
            return DebugStateResult.builder()
                    .active(false)
                    .build();
        }

        DebugStateResult.DebugStateResultBuilder resultBuilder = DebugStateResult.builder()
                .active(true);

        IJavaThread thread = debugContext.getCurrentThread();

        if (thread != null && thread.isSuspended()) {
            resultBuilder.suspended(true)
                    .thread(thread.getName())
                    .reason(debugContext.getSuspendReason())
                    .location(debugContext.getCurrentLocation());
            try {
                long threadId = thread.getThreadObject().getUniqueId();
                resultBuilder.threadId(threadId);
            } catch (Exception e) {
                resultBuilder.error("Could not read thread ID: " + e.getMessage());
            }
            try {
                IJavaStackFrame frame = debugContext.resolveFrame(thread, null);
                resultBuilder.variables(ListVariablesTool.collectVariables(frame, debugContext));
            } catch (Exception ignored) {
            }
        } else {
            resultBuilder.suspended(false);
        }

        return resultBuilder.build();
    }
}
