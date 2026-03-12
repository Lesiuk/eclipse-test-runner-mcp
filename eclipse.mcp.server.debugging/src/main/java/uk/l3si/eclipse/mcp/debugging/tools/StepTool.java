package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.debugging.model.StepResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StepTool implements McpTool {

    private static final long STEP_TIMEOUT_SECONDS = 30;

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

        // Set up a latch to wait for the step to complete (suspend or terminate)
        CountDownLatch stepComplete = new CountDownLatch(1);
        IDebugEventSetListener listener = events -> {
            for (DebugEvent event : events) {
                if (event.getSource() == thread) {
                    if (event.getKind() == DebugEvent.SUSPEND
                            && (event.getDetail() == DebugEvent.STEP_END
                                || event.getDetail() == DebugEvent.BREAKPOINT)) {
                        stepComplete.countDown();
                        return;
                    }
                    if (event.getKind() == DebugEvent.TERMINATE) {
                        stepComplete.countDown();
                        return;
                    }
                }
            }
        };

        DebugPlugin plugin = DebugPlugin.getDefault();
        if (plugin == null) {
            throw new IllegalStateException("Debug plugin is not available.");
        }
        plugin.addDebugEventListener(listener);
        try {
            // Initiate the step
            switch (action) {
                case "over" -> thread.stepOver();
                case "into" -> thread.stepInto();
                case "return" -> thread.stepReturn();
            }

            // Wait for the step to complete
            boolean completed = stepComplete.await(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException(
                        "Step '" + action + "' timed out after " + STEP_TIMEOUT_SECONDS
                        + " seconds. The thread may still be running.");
            }
        } finally {
            DebugPlugin dp = DebugPlugin.getDefault();
            if (dp != null) {
                dp.removeDebugEventListener(listener);
            }
        }

        // Build the result with the new location
        StepResult.StepResultBuilder resultBuilder = StepResult.builder()
                .action(action)
                .thread(thread.getName());

        if (thread.isTerminated()) {
            return resultBuilder
                    .terminated(true)
                    .reason("Thread terminated after step '" + action + "'. The program may have finished or thrown an unhandled exception.")
                    .build();
        }

        try {
            var frames = thread.getStackFrames();
            if (frames.length > 0 && frames[0] instanceof IJavaStackFrame frame) {
                LocationInfo.LocationInfoBuilder locationBuilder = LocationInfo.builder()
                        .className(frame.getDeclaringTypeName())
                        .method(frame.getMethodName())
                        .line(frame.getLineNumber());
                try {
                    String sourceName = frame.getSourceName();
                    if (sourceName != null) {
                        locationBuilder.sourceName(sourceName);
                    }
                } catch (Exception ignored) {}
                resultBuilder.location(locationBuilder.build());
            }
        } catch (Exception e) {
            resultBuilder.error("Could not read location after step: " + e.getMessage());
        }

        return resultBuilder.build();
    }
}
