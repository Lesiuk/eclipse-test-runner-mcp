package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.DebugStateResult;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

public class GetDebugStateTool implements IMcpTool {

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
             + "Call this after launching a test in debug mode to see if a breakpoint was hit.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder().build();
    }

    @Override
    public Object execute(Args args) throws Exception {
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
