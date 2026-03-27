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
        when(thread.isSuspended()).thenReturn(true);

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
        assertTrue(ex.getMessage().contains("get_debug_state"));
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

    // --- multi-thread race condition ---

    @Test
    void backgroundThreadSuspendResume_doesNotClobberBreakpointThread() {
        IJavaThread breakpointThread = mock(IJavaThread.class);
        IJavaThread backgroundThread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(breakpointThread.getDebugTarget()).thenReturn(target);
        when(backgroundThread.getDebugTarget()).thenReturn(target);
        when(breakpointThread.isSuspended()).thenReturn(true);
        when(backgroundThread.isSuspended()).thenReturn(true);

        // Breakpoint thread suspends
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(breakpointThread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });
        assertSame(breakpointThread, debugContext.getCurrentThread());

        // Background thread briefly suspends — should NOT overwrite
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(backgroundThread, DebugEvent.SUSPEND, DebugEvent.UNSPECIFIED)
        });
        assertSame(breakpointThread, debugContext.getCurrentThread(),
                "Background thread suspend must not overwrite breakpoint thread");

        // Background thread resumes — should NOT clear breakpoint thread
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(backgroundThread, DebugEvent.RESUME, DebugEvent.CLIENT_REQUEST)
        });
        assertSame(breakpointThread, debugContext.getCurrentThread(),
                "Background thread resume must not clear breakpoint thread");

        assertTrue(debugContext.isSuspended());
    }

    @Test
    void suspendOverwritesWhenTrackedThreadNoLongerSuspended() {
        IJavaThread thread1 = mock(IJavaThread.class);
        IJavaThread thread2 = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread1.getDebugTarget()).thenReturn(target);
        when(thread2.getDebugTarget()).thenReturn(target);

        // Thread 1 suspends
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread1, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });
        assertSame(thread1, debugContext.getCurrentThread());

        // Thread 1 is no longer suspended (e.g. resumed by evaluation)
        when(thread1.isSuspended()).thenReturn(false);

        // Thread 2 suspends — should overwrite since thread 1 is no longer suspended
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(thread2, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
        });
        assertSame(thread2, debugContext.getCurrentThread());
    }

    @Test
    void isSuspended_fallbackScansTargetThreads() {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);
        when(thread.isSuspended()).thenReturn(true);
        when(target.isTerminated()).thenReturn(false);

        // Set up target with a suspended thread, but don't set currentThread
        // (simulating the race condition where events cleared it)
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(target, DebugEvent.CREATE)
        });
        try {
            when(target.getThreads()).thenReturn(new IThread[]{thread});
        } catch (Exception ignored) {}

        // currentThread is null but the fallback should find the suspended thread
        assertTrue(debugContext.isSuspended());
        assertSame(thread, debugContext.getCurrentThread(),
                "Fallback should recover the suspended thread");
    }

    @Test
    void resolveThread_fallbackScansTargetThreads() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(thread.getDebugTarget()).thenReturn(target);
        when(thread.isSuspended()).thenReturn(true);
        when(target.isTerminated()).thenReturn(false);

        // Set up target with a suspended thread, but clear currentThread
        debugContext.handleDebugEvents(new DebugEvent[]{
                new DebugEvent(target, DebugEvent.CREATE)
        });
        try {
            when(target.getThreads()).thenReturn(new IThread[]{thread});
        } catch (Exception ignored) {}

        // resolveThread should find the suspended thread via fallback
        assertSame(thread, debugContext.resolveThread(null));
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

    // --- formatSourceContext ---

    private static final String SOURCE = String.join("\n",
            "package com.example;",             // line 1
            "",                                 // line 2
            "public class App {",               // line 3
            "    public void run() {",          // line 4
            "        int total = 0;",           // line 5
            "        for (var item : items) {", // line 6
            "            total += item.getValue();", // line 7
            "            logger.debug(\"added\", item);", // line 8
            "        }",                        // line 9
            "    }",                            // line 10
            "}");                               // line 11

    @Test
    void formatSourceContextMiddleOfFile() {
        String result = DebugContext.formatSourceContext(SOURCE, 7);
        assertNotNull(result);
        String[] lines = result.split("\n");
        assertEquals(5, lines.length);
        assertTrue(lines[0].startsWith("5:   "));
        assertTrue(lines[1].startsWith("6:   "));
        assertTrue(lines[2].startsWith("7: > "));
        assertTrue(lines[2].contains("total += item.getValue()"));
        assertTrue(lines[3].startsWith("8:   "));
        assertTrue(lines[4].startsWith("9:   "));
    }

    @Test
    void formatSourceContextFirstLine() {
        String result = DebugContext.formatSourceContext(SOURCE, 1);
        assertNotNull(result);
        String[] lines = result.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("1: > "));
        assertTrue(lines[0].contains("package com.example;"));
        assertTrue(lines[1].startsWith("2:   "));
        assertTrue(lines[2].startsWith("3:   "));
    }

    @Test
    void formatSourceContextSecondLine() {
        String result = DebugContext.formatSourceContext(SOURCE, 2);
        assertNotNull(result);
        String[] lines = result.split("\n");
        assertEquals(4, lines.length);
        assertTrue(lines[0].startsWith("1:   "));
        assertTrue(lines[1].startsWith("2: > "));
    }

    @Test
    void formatSourceContextLastLine() {
        String result = DebugContext.formatSourceContext(SOURCE, 11);
        assertNotNull(result);
        String[] lines = result.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[2].startsWith("11: > "));
    }

    @Test
    void formatSourceContextSecondToLastLine() {
        String result = DebugContext.formatSourceContext(SOURCE, 10);
        assertNotNull(result);
        String[] lines = result.split("\n");
        assertEquals(4, lines.length);
        assertTrue(lines[2].startsWith("10: > "));
    }

    @Test
    void formatSourceContextNullReturnsNull() {
        assertNull(DebugContext.formatSourceContext(null, 5));
    }

    @Test
    void formatSourceContextZeroLineReturnsNull() {
        assertNull(DebugContext.formatSourceContext(SOURCE, 0));
    }

    @Test
    void formatSourceContextNegativeLineReturnsNull() {
        assertNull(DebugContext.formatSourceContext(SOURCE, -1));
    }

    @Test
    void formatSourceContextLineOutOfRangeReturnsNull() {
        assertNull(DebugContext.formatSourceContext(SOURCE, 999));
    }

    @Test
    void formatSourceContextSingleLine() {
        String result = DebugContext.formatSourceContext("only line", 1);
        assertEquals("1: > only line", result);
    }

    @Test
    void formatSourceContextWindowsLineEndings() {
        String source = "line1\r\nline2\r\nline3\r\nline4\r\nline5";
        String result = DebugContext.formatSourceContext(source, 3);
        assertNotNull(result);
        String[] lines = result.split("\n");
        assertEquals(5, lines.length);
        assertTrue(lines[2].startsWith("3: > "));
        assertTrue(lines[2].contains("line3"));
        for (String line : lines) {
            assertFalse(line.contains("\r"));
        }
    }

    // --- isSuspended ---

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
