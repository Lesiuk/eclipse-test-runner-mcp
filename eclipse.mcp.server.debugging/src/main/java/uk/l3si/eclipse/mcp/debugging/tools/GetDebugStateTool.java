package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.DebugStateResult;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
             + "Use wait_for_suspend=true to block until a breakpoint is hit instead of polling.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("wait_for_suspend", PropertySchema.bool(
                        "If true, blocks until a thread suspends (e.g. hits a breakpoint) or the session terminates. "
                        + "Use after resume or run_test in debug mode instead of polling."))
                .property("timeout", PropertySchema.builder()
                        .type("integer")
                        .description("Timeout in seconds when wait_for_suspend is true (default: "
                                + DEFAULT_TIMEOUT_SECONDS + ").")
                        .build())
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        boolean waitForSuspend = args.getBoolean("wait_for_suspend");

        if (waitForSuspend) {
            waitForSuspendEvent(args);
        }

        return buildState();
    }

    private void waitForSuspendEvent(Args args) throws Exception {
        // Already suspended — no need to wait
        if (debugContext.isSuspended()) {
            return;
        }

        IJavaDebugTarget target = debugContext.getCurrentTarget();
        if (target == null || target.isTerminated()) {
            return;
        }

        int timeoutSeconds = args.getInt("timeout") != null
                ? args.getInt("timeout") : DEFAULT_TIMEOUT_SECONDS;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean terminated = new AtomicBoolean(false);

        IDebugEventSetListener listener = events -> {
            for (DebugEvent event : events) {
                if (event.getKind() == DebugEvent.SUSPEND
                        && event.getSource() instanceof IJavaThread) {
                    latch.countDown();
                    return;
                }
                if (event.getKind() == DebugEvent.TERMINATE
                        && event.getSource() instanceof IDebugTarget) {
                    terminated.set(true);
                    latch.countDown();
                    return;
                }
            }
        };

        DebugPlugin plugin = DebugPlugin.getDefault();
        if (plugin == null) {
            return;
        }
        plugin.addDebugEventListener(listener);
        try {
            // Re-check after registering listener to avoid race condition
            if (debugContext.isSuspended()) {
                return;
            }
            IJavaDebugTarget currentTarget = debugContext.getCurrentTarget();
            if (currentTarget == null || currentTarget.isTerminated()) {
                return;
            }
            latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            DebugPlugin dp = DebugPlugin.getDefault();
            if (dp != null) {
                dp.removeDebugEventListener(listener);
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
