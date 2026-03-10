package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.FrameInfo;
import uk.l3si.eclipse.mcp.debugging.model.StackTraceResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.ArrayList;
import java.util.List;

public class GetStackTraceTool implements IMcpTool {

    private final DebugContext debugContext;

    public GetStackTraceTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "get_stack_trace";
    }

    @Override
    public String getDescription() {
        return "Get the stack trace (list of frames) for a suspended thread. "
             + "Defaults to the thread that hit the most recent breakpoint. "
             + "Each frame shows class, method, line number, and source file name. "
             + "Use the frame index with 'inspect_variable' or 'evaluate_expression' to work in a specific frame's context.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("thread_id", PropertySchema.builder().type("integer").description("Thread ID (from list_threads). Defaults to the current suspended thread.").build())
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        Long threadId = args.getLong("thread_id");
        IJavaThread thread = debugContext.resolveThread(threadId);
        if (!thread.isSuspended()) {
            throw new IllegalStateException("Thread '" + thread.getName() + "' is not suspended.");
        }

        List<FrameInfo> frameList = new ArrayList<>();
        IStackFrame[] frames = thread.getStackFrames();
        for (int i = 0; i < frames.length; i++) {
            if (!(frames[i] instanceof IJavaStackFrame frame)) continue;

            FrameInfo.FrameInfoBuilder infoBuilder = FrameInfo.builder()
                    .index(i)
                    .className(frame.getDeclaringTypeName())
                    .method(frame.getMethodName())
                    .line(frame.getLineNumber());
            try {
                String sourceName = frame.getSourceName();
                if (sourceName != null) {
                    infoBuilder.sourceName(sourceName);
                }
            } catch (Exception ignored) {}
            frameList.add(infoBuilder.build());
        }

        return StackTraceResult.builder()
                .thread(thread.getName())
                .frames(frameList)
                .build();
    }
}
