package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.core.tools.TestResultsHelper;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.VariableCollector;
import uk.l3si.eclipse.mcp.debugging.model.StepResult;
import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.List;

public class StepTool implements McpTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final DebugContext debugContext;

    public StepTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "step";
    }

    @Override
    public String getDescription() {
        return "Control debugger execution. "
             + "'over' = next line, 'into' = enter method, 'return' = exit method, "
             + "'resume' = run until next breakpoint or termination. "
             + "Returns the new location with source context. "
             + "On termination, includes test results if available.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("action", PropertySchema.stringEnum(
                        "Step action: 'over' (next line), 'into' (enter method), 'return' (exit method), 'resume' (continue).",
                        List.of("over", "into", "return", "resume")))
                .property("thread_id", PropertySchema.builder()
                        .type("integer")
                        .description("Thread ID. Defaults to the current suspended thread.")
                        .build())
                .property("timeout", PropertySchema.builder()
                        .type("integer")
                        .description("Timeout in seconds (default: " + DEFAULT_TIMEOUT_SECONDS + ").")
                        .build())
                .required(List.of("action"))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
        String action = args.requireString("action", "Step action: over, into, return, or resume");

        if (!action.equals("over") && !action.equals("into")
                && !action.equals("return") && !action.equals("resume")) {
            throw new IllegalArgumentException(
                    "Invalid step action: '" + action + "'. Must be 'over', 'into', 'return', or 'resume'.");
        }

        Long threadId = args.getLong("thread_id");
        int timeoutSeconds = args.getInt("timeout") != null
                ? args.getInt("timeout") : DEFAULT_TIMEOUT_SECONDS;

        IJavaThread thread = debugContext.resolveThread(threadId);
        if (!thread.isSuspended()) {
            throw new IllegalStateException("Thread '" + thread.getName() + "' is not suspended.");
        }

        String threadName = thread.getName();
        // Capture launch before stepping — ILaunch.isTerminated() is the most
        // reliable termination check (avoids races with event-driven state).
        ILaunch launch = thread.getDebugTarget().getLaunch();

        switch (action) {
            case "over" -> thread.stepOver();
            case "into" -> thread.stepInto();
            case "return" -> thread.stepReturn();
            case "resume" -> thread.resume();
        }

        WaitResult wait = debugContext.waitForSuspendOrTerminate(timeoutSeconds, progress, launch);

        StepResult.StepResultBuilder result = StepResult.builder()
                .thread(threadName);

        return switch (wait) {
            case SUSPENDED -> {
                result.reason(debugContext.getSuspendReason())
                      .location(debugContext.getCurrentLocation())
                      .variables(VariableCollector.collectForCurrentFrame(debugContext));
                yield result.build();
            }
            case TERMINATED -> result
                    .terminated(true)
                    .reason("terminated")
                    .testResults(collectTestResults())
                    .build();
            case TIMEOUT -> result
                    .reason("timeout")
                    .build();
        };
    }

    private TestRunResult collectTestResults() {
        try {
            return TestResultsHelper.collect(false, message -> {});
        } catch (Exception e) {
            return null;
        }
    }
}
