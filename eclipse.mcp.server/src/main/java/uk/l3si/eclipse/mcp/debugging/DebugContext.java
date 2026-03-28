package uk.l3si.eclipse.mcp.debugging;

import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;

public class DebugContext implements IDebugEventSetListener {

    private volatile IJavaThread currentThread;
    private volatile IJavaDebugTarget currentTarget;

    public DebugContext() {
        DebugPlugin dp = DebugPlugin.getDefault();
        if (dp != null) {
            dp.addDebugEventListener(this);
        }
    }

    public void unregister() {
        DebugPlugin dp = DebugPlugin.getDefault();
        if (dp != null) {
            dp.removeDebugEventListener(this);
        }
    }

    @Override
    public synchronized void handleDebugEvents(DebugEvent[] events) {
        for (DebugEvent event : events) {
            Object source = event.getSource();
            switch (event.getKind()) {
                case DebugEvent.CREATE -> {
                    if (source instanceof IJavaDebugTarget target) {
                        currentTarget = target;
                    }
                }
                case DebugEvent.SUSPEND -> {
                    if (source instanceof IJavaThread thread) {
                        // Only update if we're not already tracking a suspended
                        // thread.  In multi-threaded VMs (Quarkus, Spring Boot,
                        // etc.), background threads can briefly suspend/resume
                        // and overwrite the breakpoint thread, causing
                        // waitForSuspendOrTerminate to miss the suspension.
                        if (currentThread == null || !currentThread.isSuspended()) {
                            currentThread = thread;
                        }
                        try {
                            currentTarget = (IJavaDebugTarget) thread.getDebugTarget();
                        } catch (Exception ignored) {}
                    }
                }
                case DebugEvent.RESUME -> {
                    if (source instanceof IJavaThread thread && thread == currentThread) {
                        currentThread = null;
                    }
                }
                case DebugEvent.TERMINATE -> {
                    if (source instanceof IDebugTarget) {
                        currentThread = null;
                        currentTarget = null;
                    } else if (source instanceof IJavaThread thread && thread == currentThread) {
                        currentThread = null;
                    }
                }
            }
        }
    }

    /**
     * Get the current debug target, or null if no debug session is active.
     */
    public synchronized IJavaDebugTarget getCurrentTarget() {
        return currentTarget;
    }

    /**
     * Get the currently suspended thread, or null.
     */
    public synchronized IJavaThread getCurrentThread() {
        return currentThread;
    }

    /**
     * Resolve a thread by ID, or return the current thread if no ID given.
     * Thread ID comes from IJavaThread.getThreadObject().getUniqueId() or Thread.getId().
     */
    public synchronized IJavaThread resolveThread(Long threadId) throws Exception {
        if (threadId == null) {
            IJavaThread thread = currentThread;
            if (thread == null || !thread.isSuspended()) {
                // Fallback: scan threads directly in case events were missed
                thread = findAnySuspendedThread();
                if (thread != null) {
                    currentThread = thread;
                    return thread;
                }
                if (currentTarget != null && !currentTarget.isTerminated()) {
                    throw new IllegalStateException(
                            "Debug session is active but no thread is currently suspended. "
                            + "Use 'get_debug_state' to check the current state, or step with action='resume' may have been called already.");
                }
                throw new IllegalStateException(
                        "No debug session active. Launch a test with mode='debug' first.");
            }
            return thread;
        }

        IJavaDebugTarget target = currentTarget;
        if (target == null) {
            throw new IllegalStateException(
                    "No debug session active. Launch a test with mode='debug' first.");
        }

        for (IThread t : target.getThreads()) {
            if (t instanceof IJavaThread jt) {
                if (jt.getThreadObject() != null && jt.getThreadObject().getUniqueId() == threadId) {
                    return jt;
                }
            }
        }
        throw new IllegalArgumentException("Thread not found with ID: " + threadId);
    }

    /**
     * Resolve a stack frame from a thread, defaulting to top frame (index 0).
     */
    public IJavaStackFrame resolveFrame(IJavaThread thread, Integer frameIndex) throws Exception {
        if (!thread.isSuspended()) {
            throw new IllegalStateException("Thread '" + thread.getName() + "' is not suspended.");
        }
        var frames = thread.getStackFrames();
        if (frames.length == 0) {
            throw new IllegalStateException("Thread '" + thread.getName() + "' has no stack frames.");
        }
        int index = frameIndex != null ? frameIndex : 0;
        if (index < 0 || index >= frames.length) {
            throw new IllegalArgumentException("Frame index " + index + " out of range (0-" + (frames.length - 1) + ").");
        }
        if (frames[index] instanceof IJavaStackFrame jsf) {
            return jsf;
        }
        throw new IllegalStateException("Frame at index " + index + " is not a Java stack frame.");
    }

    /**
     * Check if there's an active debug session with a suspended thread.
     * Falls back to scanning all threads in the target if the tracked
     * thread reference was lost due to event ordering.
     */
    public synchronized boolean isSuspended() {
        IJavaThread thread = currentThread;
        if (thread != null && thread.isSuspended()) return true;

        // Fallback: scan the target's threads directly.  This handles
        // cases where event ordering caused currentThread to be cleared
        // even though a thread is still suspended at a breakpoint.
        IJavaThread found = findAnySuspendedThread();
        if (found != null) {
            currentThread = found;
            return true;
        }
        return false;
    }

    /**
     * Scan the target's threads for any that are currently suspended.
     * Returns the first suspended thread found, or null.
     */
    private IJavaThread findAnySuspendedThread() {
        IJavaDebugTarget target = currentTarget;
        if (target == null || target.isTerminated()) return null;
        try {
            for (IThread t : target.getThreads()) {
                if (t instanceof IJavaThread jt && jt.isSuspended()) {
                    return jt;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public enum WaitResult { SUSPENDED, TERMINATED, TIMEOUT }

    private static final int POLL_INTERVAL_MS = 500;
    private static final long KEEPALIVE_INTERVAL_MS = 60_000;

    /**
     * Poll until a thread suspends, the target terminates, or the timeout expires.
     */
    public WaitResult waitForSuspendOrTerminate(int timeoutSeconds) throws InterruptedException {
        return waitForSuspendOrTerminate(timeoutSeconds, message -> {}, null);
    }

    public WaitResult waitForSuspendOrTerminate(int timeoutSeconds, ProgressReporter progress) throws InterruptedException {
        return waitForSuspendOrTerminate(timeoutSeconds, progress, null);
    }

    /**
     * Poll until a thread suspends, the launch terminates, or the timeout expires.
     *
     * @param launch if provided, termination is checked via the launch directly
     *               (avoids a race where the CREATE event has not yet set
     *               {@code currentTarget} after the launch returns).
     *               The pre-loop suspension check is also skipped because the
     *               new test JVM may not have started yet and stale state from
     *               a previous session could cause a false positive.
     */
    public WaitResult waitForSuspendOrTerminate(int timeoutSeconds, ProgressReporter progress,
            ILaunch launch) throws InterruptedException {
        // When called after a fresh launch, skip the pre-loop check — the
        // new JVM has not started yet and isSuspended() could see stale
        // state from a previous debug session or a JVM startup suspension.
        // For non-launch callers (e.g. StepTool) the pre-loop check gives
        // a faster response.
        if (launch == null && isSuspended()) return WaitResult.SUSPENDED;

        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        long lastProgressTime = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            if (isSuspended()) return WaitResult.SUSPENDED;
            if (isTerminated(launch)) return WaitResult.TERMINATED;
            if (System.currentTimeMillis() - lastProgressTime >= KEEPALIVE_INTERVAL_MS) {
                progress.report("Waiting for breakpoint...");
                lastProgressTime = System.currentTimeMillis();
            }
        }
        return WaitResult.TIMEOUT;
    }

    /**
     * Check whether the debug session has terminated.  Uses the launch if
     * available (reliable, no event-delivery race); falls back to the
     * event-tracked {@code currentTarget} otherwise.  A null target is NOT
     * treated as terminated — it just means the CREATE event hasn't arrived.
     */
    private boolean isTerminated(ILaunch launch) {
        if (launch != null) return launch.isTerminated();
        IJavaDebugTarget target = getCurrentTarget();
        return target != null && target.isTerminated();
    }

    /**
     * Build a {@link LocationInfo} from the top frame of the current suspended thread,
     * or {@code null} if unavailable.
     */
    public LocationInfo getCurrentLocation() {
        IJavaThread thread = currentThread;
        if (thread == null || !thread.isSuspended()) return null;
        try {
            var frames = thread.getStackFrames();
            if (frames.length > 0 && frames[0] instanceof IJavaStackFrame frame) {
                String className = frame.getDeclaringTypeName();
                int lineNumber = frame.getLineNumber();
                LocationInfo.LocationInfoBuilder loc = LocationInfo.builder()
                        .className(className)
                        .method(frame.getMethodName())
                        .line(lineNumber);
                try {
                    String sourceName = frame.getSourceName();
                    if (sourceName != null) {
                        loc.sourceName(sourceName);
                    }
                } catch (Exception ignored) {}
                loc.source(lookupSourceContext(className, lineNumber));
                return loc.build();
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Look up source lines around the given line for a class.
     * Returns formatted lines with line numbers, marking the current line with '>'.
     * Returns null if source is unavailable (best-effort).
     */
    private String lookupSourceContext(String className, int line) {
        if (className == null || line <= 0) return null;
        try {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (IProject project : projects) {
                if (!project.isOpen() || !project.hasNature(JavaCore.NATURE_ID)) continue;
                IJavaProject javaProject = JavaCore.create(project);
                IType type = javaProject.findType(className);
                if (type == null || !type.exists()) continue;
                if (type.getTypeRoot() == null) continue;
                String source = type.getTypeRoot().getSource();
                if (source == null) continue;

                return formatSourceContext(source, line);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Format source lines around the given 1-based line number.
     * Returns ~5 lines with line numbers, marking the current line with '>'.
     * Returns null if the source is null or the line is out of range.
     */
    static String formatSourceContext(String source, int line) {
        if (source == null || line <= 0) return null;
        String[] lines = source.split("\\R", -1);
        int currentIdx = line - 1;
        if (currentIdx >= lines.length) return null;
        int start = Math.max(0, currentIdx - 2);
        int end = Math.min(lines.length - 1, currentIdx + 2);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (sb.length() > 0) sb.append('\n');
            int lineNum = i + 1;
            if (lineNum == line) {
                sb.append(lineNum).append(": > ").append(lines[i]);
            } else {
                sb.append(lineNum).append(":   ").append(lines[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Return "breakpoint" if the current thread hit a breakpoint, "suspended" otherwise,
     * or {@code null} if no thread is suspended.
     */
    public String getSuspendReason() {
        IJavaThread thread = currentThread;
        if (thread == null || !thread.isSuspended()) return null;
        try {
            var breakpoints = thread.getBreakpoints();
            return (breakpoints != null && breakpoints.length > 0) ? "breakpoint" : "suspended";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
