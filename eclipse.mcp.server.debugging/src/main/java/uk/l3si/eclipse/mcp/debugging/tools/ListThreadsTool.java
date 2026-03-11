package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ThreadInfo;
import uk.l3si.eclipse.mcp.debugging.model.ThreadListResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

import java.util.ArrayList;
import java.util.List;

public class ListThreadsTool implements IMcpTool {

    private final DebugContext debugContext;

    public ListThreadsTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "list_threads";
    }

    @Override
    public String getDescription() {
        return "List all threads in the current debug session with their state (running, suspended, terminated). "
             + "Suspended threads show their current location. Use the thread ID to inspect specific threads.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder().build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        IJavaDebugTarget target = debugContext.getCurrentTarget();
        if (target == null || target.isTerminated()) {
            throw new IllegalStateException("No active debug session.");
        }

        List<ThreadInfo> threadList = new ArrayList<>();
        for (IThread t : target.getThreads()) {
            if (!(t instanceof IJavaThread jt)) continue;

            ThreadInfo.ThreadInfoBuilder infoBuilder = ThreadInfo.builder()
                    .name(jt.getName());
            try {
                infoBuilder.id(jt.getThreadObject().getUniqueId());
            } catch (Exception e) {
                infoBuilder.error("Could not read thread ID: " + e.getMessage());
            }

            if (jt.isTerminated()) {
                infoBuilder.state("terminated");
            } else if (jt.isSuspended()) {
                infoBuilder.state("suspended");
                try {
                    var frames = jt.getStackFrames();
                    if (frames.length > 0 && frames[0] instanceof IJavaStackFrame frame) {
                        infoBuilder.location(frame.getDeclaringTypeName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
                    }
                } catch (Exception e) {
                    infoBuilder.error("Could not read location: " + e.getMessage());
                }
            } else {
                infoBuilder.state("running");
            }

            threadList.add(infoBuilder.build());
        }

        return ThreadListResult.builder()
                .threads(threadList)
                .build();
    }
}
