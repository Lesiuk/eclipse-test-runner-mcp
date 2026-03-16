package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private ResumeTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new ResumeTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsResume() {
        assertEquals("resume", tool.getName());
    }

    @Test
    void resumeAndHitBreakpoint() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        // After resume, poll finds suspended state immediately
        when(debugContext.isSuspended()).thenReturn(true);
        when(debugContext.getCurrentThread()).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.App");
        when(frame.getMethodName()).thenReturn("doStuff");
        when(frame.getLineNumber()).thenReturn(42);
        when(frame.getSourceName()).thenReturn("App.java");
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});
        when(thread.getBreakpoints()).thenReturn(new IBreakpoint[]{mock(IBreakpoint.class)});

        JsonObject args = new JsonObject();
        args.addProperty("timeout", 2);
        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertTrue(result.get("stopped").getAsBoolean());
        assertEquals("breakpoint", result.get("reason").getAsString());

        JsonObject location = result.getAsJsonObject("location");
        assertEquals("com.example.App", location.get("class").getAsString());
        assertEquals("doStuff", location.get("method").getAsString());
        assertEquals(42, location.get("line").getAsInt());
    }

    @Test
    void resumeAndTerminate() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        // After resume, target is terminated
        when(debugContext.isSuspended()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(null);

        JsonObject args = new JsonObject();
        args.addProperty("timeout", 2);
        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertTrue(result.get("stopped").getAsBoolean());
        assertEquals("terminated", result.get("reason").getAsString());
    }

    @Test
    void resumeAndTimeout() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        // After resume, thread keeps running — never suspends, never terminates
        when(debugContext.isSuspended()).thenReturn(false);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        JsonObject args = new JsonObject();
        args.addProperty("timeout", 1);
        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertFalse(result.get("stopped").getAsBoolean());
        assertEquals("timeout", result.get("reason").getAsString());
    }

    @Test
    void notSuspendedThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(null)));
        assertTrue(ex.getMessage().contains("not suspended"));
    }

    @Test
    void noSessionThrows() throws Exception {
        when(debugContext.resolveThread(null))
                .thenThrow(new IllegalStateException("No debug session active."));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(null)));
        assertTrue(ex.getMessage().contains("No debug session"));
    }

    @Test
    void threadNotFoundThrows() throws Exception {
        when(debugContext.resolveThread(999L))
                .thenThrow(new IllegalArgumentException("Thread not found with ID: 999"));

        JsonObject args = new JsonObject();
        args.addProperty("thread_id", "999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("999"));
    }
}
