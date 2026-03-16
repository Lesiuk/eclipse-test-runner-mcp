package uk.l3si.eclipse.mcp.debugging;

import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IThread;
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
                        currentThread = thread;
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
            if (thread == null) {
                if (currentTarget != null && !currentTarget.isTerminated()) {
                    throw new IllegalStateException(
                            "Debug session is active but no thread is currently suspended. "
                            + "Use 'list_threads' to see all threads, or 'resume' may have been called already.");
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
     */
    public synchronized boolean isSuspended() {
        IJavaThread thread = currentThread;
        return thread != null && thread.isSuspended();
    }

    public enum WaitResult { SUSPENDED, TERMINATED, TIMEOUT }

    private static final int POLL_INTERVAL_MS = 500;

    /**
     * Poll until a thread suspends, the target terminates, or the timeout expires.
     */
    public WaitResult waitForSuspendOrTerminate(int timeoutSeconds) throws InterruptedException {
        if (isSuspended()) return WaitResult.SUSPENDED;

        IJavaDebugTarget target = getCurrentTarget();
        if (target == null || target.isTerminated()) return WaitResult.TERMINATED;

        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_INTERVAL_MS);
            if (isSuspended()) return WaitResult.SUSPENDED;
            target = getCurrentTarget();
            if (target == null || target.isTerminated()) return WaitResult.TERMINATED;
        }
        return WaitResult.TIMEOUT;
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
                LocationInfo.LocationInfoBuilder loc = LocationInfo.builder()
                        .className(frame.getDeclaringTypeName())
                        .method(frame.getMethodName())
                        .line(frame.getLineNumber());
                try {
                    String sourceName = frame.getSourceName();
                    if (sourceName != null) {
                        loc.sourceName(sourceName);
                    }
                } catch (Exception ignored) {}
                return loc.build();
            }
        } catch (Exception ignored) {}
        return null;
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
