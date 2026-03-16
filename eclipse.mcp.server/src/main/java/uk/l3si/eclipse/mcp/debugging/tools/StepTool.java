package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.model.StepResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.List;

public class StepTool implements McpTool {

    private static final int STEP_TIMEOUT_SECONDS = 30;

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
        return "Perform a step operation in the debugger. "
             + "'over' steps to the next line, 'into' steps into a method call, "
             + "'return' steps out of the current method. "
             + "Waits for the step to complete and returns the new location.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("action", PropertySchema.stringEnum(
                        "Step action: 'over' (next line), 'into' (enter method), 'return' (exit method).",
                        List.of("over", "into", "return")))
                .property("thread_id", PropertySchema.builder()
                        .type("integer")
                        .description("Thread ID (from list_threads). Defaults to the current suspended thread.")
                        .build())
                .required(List.of("action"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "Step action: over, into, or return");

        if (!action.equals("over") && !action.equals("into") && !action.equals("return")) {
            throw new IllegalArgumentException("Invalid step action: '" + action + "'. Must be 'over', 'into', or 'return'.");
        }

        Long threadId = args.getLong("thread_id");
        IJavaThread thread = debugContext.resolveThread(threadId);
        if (!thread.isSuspended()) {
            throw new IllegalStateException("Thread '" + thread.getName() + "' is not suspended.");
        }

        String threadName = thread.getName();

        switch (action) {
            case "over" -> thread.stepOver();
            case "into" -> thread.stepInto();
            case "return" -> thread.stepReturn();
        }

        WaitResult wait = debugContext.waitForSuspendOrTerminate(STEP_TIMEOUT_SECONDS);

        StepResult.StepResultBuilder result = StepResult.builder()
                .action(action)
                .thread(threadName);

        return switch (wait) {
            case SUSPENDED -> result
                    .location(debugContext.getCurrentLocation())
                    .build();
            case TERMINATED -> result
                    .terminated(true)
                    .reason("Thread terminated after step '" + action
                            + "'. The program may have finished or thrown an unhandled exception.")
                    .build();
            case TIMEOUT -> result
                    .terminated(false)
                    .reason("Step '" + action + "' timed out after " + STEP_TIMEOUT_SECONDS
                            + " seconds. The thread may still be running.")
                    .build();
        };
    }
}
