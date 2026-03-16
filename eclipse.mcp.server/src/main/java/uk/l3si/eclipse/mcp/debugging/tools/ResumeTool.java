package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.model.ResumeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.jdt.debug.core.IJavaThread;

public class ResumeTool implements McpTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private final DebugContext debugContext;

    public ResumeTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "resume";
    }

    @Override
    public String getDescription() {
        return "Resume execution of a suspended thread. "
             + "Blocks until the thread hits a breakpoint, terminates, or the timeout is reached. "
             + "Returns the new stop location when a breakpoint is hit. "
             + "Defaults to the current suspended thread if no thread_id is given.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("thread_id", PropertySchema.builder()
                        .type("integer")
                        .description("Thread ID (from list_threads). Defaults to the current suspended thread.")
                        .build())
                .property("timeout", PropertySchema.builder()
                        .type("integer")
                        .description("Timeout in seconds (default: " + DEFAULT_TIMEOUT_SECONDS + ").")
                        .build())
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        Long threadId = args.getLong("thread_id");
        IJavaThread thread = debugContext.resolveThread(threadId);
        if (!thread.isSuspended()) {
            throw new IllegalStateException("Thread '" + thread.getName() + "' is not suspended.");
        }

        int timeoutSeconds = args.getInt("timeout") != null
                ? args.getInt("timeout") : DEFAULT_TIMEOUT_SECONDS;

        String threadName = thread.getName();
        thread.resume();

        WaitResult wait = debugContext.waitForSuspendOrTerminate(timeoutSeconds);

        ResumeResult.ResumeResultBuilder result = ResumeResult.builder()
                .thread(threadName);

        return switch (wait) {
            case SUSPENDED -> result
                    .stopped(true)
                    .reason(debugContext.getSuspendReason())
                    .location(debugContext.getCurrentLocation())
                    .build();
            case TERMINATED -> result
                    .stopped(true)
                    .reason("terminated")
                    .build();
            case TIMEOUT -> result
                    .stopped(false)
                    .reason("timeout")
                    .build();
        };
    }
}
