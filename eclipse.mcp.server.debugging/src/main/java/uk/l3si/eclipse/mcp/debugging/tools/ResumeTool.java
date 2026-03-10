package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ResumeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.jdt.debug.core.IJavaThread;

public class ResumeTool implements IMcpTool {

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
             + "The thread will continue running until it hits another breakpoint or terminates. "
             + "Defaults to the current suspended thread if no thread_id is given.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("thread_id", PropertySchema.builder()
                        .type("integer")
                        .description("Thread ID (from list_threads). Defaults to the current suspended thread.")
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

        thread.resume();

        return ResumeResult.builder()
                .resumed(true)
                .thread(thread.getName())
                .build();
    }
}
