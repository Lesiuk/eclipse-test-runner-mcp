package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetDebugStateToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private GetDebugStateTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new GetDebugStateTool(debugContext);
    }

    private JsonObject executeAndSerialize() throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(null))).getAsJsonObject();
    }

    @Test
    void nameIsGetDebugState() {
        assertEquals("get_debug_state", tool.getName());
    }

    @Test
    void noActiveSession() throws Exception {
        when(debugContext.getCurrentTarget()).thenReturn(null);

        JsonObject result = executeAndSerialize();
        assertFalse(result.get("active").getAsBoolean());
    }

    @Test
    void terminatedTarget() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(true);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        JsonObject result = executeAndSerialize();
        assertFalse(result.get("active").getAsBoolean());
    }

    @Test
    void activeButNotSuspended() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(debugContext.getCurrentThread()).thenReturn(null);

        JsonObject result = executeAndSerialize();
        assertTrue(result.get("active").getAsBoolean());
        assertFalse(result.get("suspended").getAsBoolean());
    }

    @Test
    void activeThreadNotSuspended() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(debugContext.getCurrentThread()).thenReturn(thread);

        JsonObject result = executeAndSerialize();
        assertTrue(result.get("active").getAsBoolean());
        assertFalse(result.get("suspended").getAsBoolean());
    }

    @Test
    void suspendedAtBreakpoint() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        IJavaObject threadObj = mock(IJavaObject.class);
        when(threadObj.getUniqueId()).thenReturn(42L);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(debugContext.getCurrentThread()).thenReturn(thread);

        // Stack frame
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.MyService");
        when(frame.getMethodName()).thenReturn("doWork");
        when(frame.getLineNumber()).thenReturn(25);
        when(frame.getSourceName()).thenReturn("MyService.java");
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        // Breakpoint reason
        IBreakpoint bp = mock(IBreakpoint.class);
        when(thread.getBreakpoints()).thenReturn(new IBreakpoint[]{bp});

        JsonObject result = executeAndSerialize();
        assertTrue(result.get("active").getAsBoolean());
        assertTrue(result.get("suspended").getAsBoolean());
        assertEquals("main", result.get("thread").getAsString());
        assertEquals(42L, result.get("threadId").getAsLong());
        assertEquals("breakpoint", result.get("reason").getAsString());

        JsonObject location = result.getAsJsonObject("location");
        assertEquals("com.example.MyService", location.get("class").getAsString());
        assertEquals("doWork", location.get("method").getAsString());
        assertEquals(25, location.get("line").getAsInt());
        assertEquals("MyService.java", location.get("sourceName").getAsString());
    }

    @Test
    void suspendedAfterStep() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        IJavaObject threadObj = mock(IJavaObject.class);
        when(threadObj.getUniqueId()).thenReturn(1L);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(debugContext.getCurrentThread()).thenReturn(thread);

        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});
        when(thread.getBreakpoints()).thenReturn(new IBreakpoint[]{});

        JsonObject result = executeAndSerialize();
        assertEquals("step", result.get("reason").getAsString());
    }

    @Test
    void waitForSuspendAlreadySuspended() throws Exception {
        // Should return immediately without registering a listener
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(debugContext.isSuspended()).thenReturn(true);

        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        IJavaObject threadObj = mock(IJavaObject.class);
        when(threadObj.getUniqueId()).thenReturn(1L);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});
        when(thread.getBreakpoints()).thenReturn(new IBreakpoint[]{});
        when(debugContext.getCurrentThread()).thenReturn(thread);

        JsonObject args = new JsonObject();
        args.addProperty("wait_for_suspend", true);

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
        assertTrue(result.get("active").getAsBoolean());
        assertTrue(result.get("suspended").getAsBoolean());
    }

    @Test
    void waitForSuspendNoActiveSession() throws Exception {
        // No debug session — should return immediately with active=false
        when(debugContext.getCurrentTarget()).thenReturn(null);
        when(debugContext.isSuspended()).thenReturn(false);

        JsonObject args = new JsonObject();
        args.addProperty("wait_for_suspend", true);

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
        assertFalse(result.get("active").getAsBoolean());
    }

    @Test
    void waitForSuspendReceivesSuspendEvent() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.isSuspended()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        IJavaObject threadObj = mock(IJavaObject.class);
        when(threadObj.getUniqueId()).thenReturn(42L);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});
        when(thread.getBreakpoints()).thenReturn(new IBreakpoint[]{mock(IBreakpoint.class)});
        when(debugContext.getCurrentThread()).thenReturn(thread);

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            // Fire suspend event when listener is registered
            doAnswer(invocation -> {
                IDebugEventSetListener listener = invocation.getArgument(0);
                listener.handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
                });
                return null;
            }).when(plugin).addDebugEventListener(any());

            JsonObject args = new JsonObject();
            args.addProperty("wait_for_suspend", true);
            args.addProperty("timeout", "5");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertTrue(result.get("active").getAsBoolean());
            assertTrue(result.get("suspended").getAsBoolean());
            assertEquals("breakpoint", result.get("reason").getAsString());

            // Verify listener is cleaned up
            verify(plugin).removeDebugEventListener(any());
        }
    }

    @Test
    void waitForSuspendReceivesTerminateEvent() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.isSuspended()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        DebugPlugin plugin = mock(DebugPlugin.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            // Fire terminate event when listener is registered
            doAnswer(invocation -> {
                IDebugEventSetListener listener = invocation.getArgument(0);
                listener.handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(target, DebugEvent.TERMINATE)
                });
                return null;
            }).when(plugin).addDebugEventListener(any());

            // After terminate, target is terminated
            when(target.isTerminated()).thenReturn(true);

            JsonObject args = new JsonObject();
            args.addProperty("wait_for_suspend", true);
            args.addProperty("timeout", "5");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertFalse(result.get("active").getAsBoolean());
        }
    }

    @Test
    void waitForSuspendListenerCleanedUp() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.isSuspended()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(debugContext.getCurrentThread()).thenReturn(null);

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            IJavaThread thread = mock(IJavaThread.class);
            doAnswer(invocation -> {
                IDebugEventSetListener listener = invocation.getArgument(0);
                listener.handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
                });
                return null;
            }).when(plugin).addDebugEventListener(any());

            JsonObject args = new JsonObject();
            args.addProperty("wait_for_suspend", true);

            tool.execute(new Args(args));

            verify(plugin).addDebugEventListener(any());
            verify(plugin).removeDebugEventListener(any());
        }
    }

    @Test
    void suspendedWithBreakpointException() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        IJavaObject threadObj = mock(IJavaObject.class);
        when(threadObj.getUniqueId()).thenReturn(1L);
        when(thread.getThreadObject()).thenReturn(threadObj);
        when(debugContext.getCurrentThread()).thenReturn(thread);

        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});
        when(thread.getBreakpoints()).thenThrow(new RuntimeException("error"));

        JsonObject result = executeAndSerialize();
        assertEquals("unknown", result.get("reason").getAsString());
    }
}
