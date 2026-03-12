package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.DebugStateResult;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

public class GetDebugStateTool implements IMcpTool {

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
            waitForSuspend(args);
        }

        return buildState();
    }

    private static final int POLL_INTERVAL_MS = 500;

    private void waitForSuspend(Args args) throws Exception {
        if (debugContext.isSuspended()) {
            return;
        }

        IJavaDebugTarget target = debugContext.getCurrentTarget();
        if (target == null || target.isTerminated()) {
            return;
        }

        int timeoutSeconds = args.getInt("timeout") != null
                ? args.getInt("timeout") : DEFAULT_TIMEOUT_SECONDS;
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            if (debugContext.isSuspended()) {
                return;
            }
            target = debugContext.getCurrentTarget();
            if (target == null || target.isTerminated()) {
                return;
            }
        }
    }

    private Object buildState() throws Exception {
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
                    .thread(thread.getName());
            try {
                long threadId = thread.getThreadObject().getUniqueId();
                resultBuilder.threadId(threadId);
            } catch (Exception e) {
                resultBuilder.error("Could not read thread ID: " + e.getMessage());
            }

            try {
                var frames = thread.getStackFrames();
                if (frames.length > 0 && frames[0] instanceof IJavaStackFrame frame) {
                    LocationInfo.LocationInfoBuilder locationBuilder = LocationInfo.builder()
                            .className(frame.getDeclaringTypeName())
                            .method(frame.getMethodName())
                            .line(frame.getLineNumber());
                    if (frame.getSourceName() != null) {
                        locationBuilder.sourceName(frame.getSourceName());
                    }
                    resultBuilder.location(locationBuilder.build());
                }
            } catch (Exception e) {
                resultBuilder.error("Could not read stack frame: " + e.getMessage());
            }

            // Include reason if available
            try {
                var breakpoints = thread.getBreakpoints();
                if (breakpoints != null && breakpoints.length > 0) {
                    resultBuilder.reason("breakpoint");
                } else {
                    resultBuilder.reason("step");
                }
            } catch (Exception e) {
                resultBuilder.reason("unknown");
            }
        } else {
            resultBuilder.suspended(false);
        }

        return resultBuilder.build();
    }
}
