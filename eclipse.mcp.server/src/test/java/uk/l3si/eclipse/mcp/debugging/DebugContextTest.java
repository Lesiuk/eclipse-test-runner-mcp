package uk.l3si.eclipse.mcp.debugging;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DebugContextTest {

    private DebugContext debugContext;

    @BeforeEach
    void setUp() {
        debugContext = new DebugContext();
    }

    // --- handleDebugEvents ---

    @Test
    void suspendEventSetsCurrentThreadAndTarget() {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);

        DebugEvent event = new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT);
        debugContext.handleDebugEvents(new DebugEvent[]{event});

        assertSame(thread, debugContext.getCurrentThread());
        assertSame(target, debugContext.getCurrentTarget());
    }

    @Test
    void suspendEventIgnoresNonJavaThread() {
        IThread nonJavaThread = mock(IThread.class);

        DebugEvent event = new DebugEvent(nonJavaThread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT);
        debugContext.handleDebugEvents(new DebugEvent[]{event});

        assertNull(debugContext.getCurrentThread());
        assertNull(debugContext.getCurrentTarget());
    }

    @Test
    void createEventSetsCurrentTarget() {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);

        DebugEvent event = new DebugEvent(target, DebugEvent.CREATE);
        debugContext.handleDebugEvents(new DebugEvent[]{event});

        assertSame(target, debugContext.getCurrentTarget());
        assertNull(debugContext.getCurrentThread());
    }

    @Test
    void createEventIgnoresNonJavaTarget() {
        IDebugTarget nonJavaTarget = mock(IDebugTarget.class);

        DebugEvent event = new DebugEvent(nonJavaTarget, DebugEvent.CREATE);
        debugContext.handleDebugEvents(new DebugEvent[]{event});

        assertNull(debugContext.getCurrentTarget());
    }

    @Test
    void resumeEventClearsCurrentThread() {
        // First suspend to set current thread
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        // Then resume
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.CLIENT_REQUEST)
        });

        assertNull(debugContext.getCurrentThread());
        // Target should remain
        assertSame(target, debugContext.getCurrentTarget());
    }

    @Test
    void resumeEventForDifferentThreadDoesNotClearCurrent() {
        IJavaThread thread1 = mock(IJavaThread.class);
        IJavaThread thread2 = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread1.getDebugTarget()).thenReturn(target);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread1, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        // Resume a different thread — should not affect current
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread2, DebugEvent.RESUME, DebugEvent.CLIENT_REQUEST)
        });

        assertSame(thread1, debugContext.getCurrentThread());
    }

    @Test
    void terminateTargetClearsBothThreadAndTarget() {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        // Target terminates
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(target, DebugEvent.TERMINATE)
        });

        assertNull(debugContext.getCurrentThread());
        assertNull(debugContext.getCurrentTarget());
    }

    @Test
    void terminateCurrentThreadClearsThread() {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        // Thread itself terminates
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.TERMINATE)
        });

        assertNull(debugContext.getCurrentThread());
        // Target still there
        assertSame(target, debugContext.getCurrentTarget());
    }

    @Test
    void terminateDifferentThreadDoesNotClearCurrent() {
        IJavaThread thread1 = mock(IJavaThread.class);
        IJavaThread thread2 = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread1.getDebugTarget()).thenReturn(target);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread1, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread2, DebugEvent.TERMINATE)
        });

        assertSame(thread1, debugContext.getCurrentThread());
    }

    // --- resolveThread ---

    @Test
    void resolveThreadReturnsCurrentWhenNoIdGiven() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        assertSame(thread, debugContext.resolveThread(null));
    }

    @Test
    void resolveThreadThrowsNoSessionWhenNothingActive() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> debugContext.resolveThread(null));
        assertTrue(ex.getMessage().contains("No debug session active"));
    }

    @Test
    void resolveThreadThrowsNotSuspendedWhenTargetActiveButNoThread() throws Exception {
        // Set up a target without a current thread (e.g., after resume)
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);
        when(target.isTerminated()).thenReturn(false);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.CLIENT_REQUEST)
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> debugContext.resolveThread(null));
        assertTrue(ex.getMessage().contains("no thread is currently suspended"));
        assertTrue(ex.getMessage().contains("list_threads"));
    }

    @Test
    void resolveThreadByIdFindsMatchingThread() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        IJavaObject threadObj = mock(IJavaObject.class);
        when(thread.getDebugTarget()).thenReturn(target);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(threadObj.getUniqueId()).thenReturn(42L);
        when(target.getThreads()).thenReturn(new IThread[]{thread});

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        assertSame(thread, debugContext.resolveThread(42L));
    }

    @Test
    void resolveThreadByIdThrowsWhenNotFound() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        IJavaObject threadObj = mock(IJavaObject.class);
        when(thread.getDebugTarget()).thenReturn(target);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(threadObj.getUniqueId()).thenReturn(42L);
        when(target.getThreads()).thenReturn(new IThread[]{thread});

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debugContext.resolveThread(999L));
        assertTrue(ex.getMessage().contains("999"));
    }

    @Test
    void resolveThreadByIdThrowsWhenNoSession() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> debugContext.resolveThread(42L));
        assertTrue(ex.getMessage().contains("No debug session active"));
    }

    // --- resolveFrame ---

    @Test
    void resolveFrameReturnsTopFrameByDefault() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame topFrame = mock(IJavaStackFrame.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{topFrame});

        assertSame(topFrame, debugContext.resolveFrame(thread, null));
    }

    @Test
    void resolveFrameReturnsFrameAtIndex() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame0 = mock(IJavaStackFrame.class);
        IJavaStackFrame frame1 = mock(IJavaStackFrame.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame0, frame1});

        assertSame(frame1, debugContext.resolveFrame(thread, 1));
    }

    @Test
    void resolveFrameThrowsWhenNotSuspended() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(thread.getName()).thenReturn("main");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> debugContext.resolveFrame(thread, null));
        assertTrue(ex.getMessage().contains("main"));
        assertTrue(ex.getMessage().contains("not suspended"));
    }

    @Test
    void resolveFrameThrowsWhenNoFrames() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> debugContext.resolveFrame(thread, null));
        assertTrue(ex.getMessage().contains("no stack frames"));
    }

    @Test
    void resolveFrameThrowsWhenIndexOutOfRange() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debugContext.resolveFrame(thread, 5));
        assertTrue(ex.getMessage().contains("out of range"));
    }

    @Test
    void resolveFrameThrowsWhenNegativeIndex() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> debugContext.resolveFrame(thread, -1));
        assertTrue(ex.getMessage().contains("out of range"));
    }

    @Test
    void resolveFrameThrowsWhenNotJavaFrame() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IStackFrame nonJavaFrame = mock(IStackFrame.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{nonJavaFrame});

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> debugContext.resolveFrame(thread, 0));
        assertTrue(ex.getMessage().contains("not a Java stack frame"));
    }

    // --- isSuspended ---

    @Test
    void isSuspendedReturnsFalseWhenNoThread() {
        assertFalse(debugContext.isSuspended());
    }

    @Test
    void isSuspendedReturnsTrueWhenThreadSuspended() {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);
        when(thread.isSuspended()).thenReturn(true);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });

        assertTrue(debugContext.isSuspended());
    }

    @Test
    void isSuspendedReturnsFalseAfterResume() {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);

        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread, DebugEvent.RESUME, DebugEvent.CLIENT_REQUEST)
        });

        assertFalse(debugContext.isSuspended());
    }
}
