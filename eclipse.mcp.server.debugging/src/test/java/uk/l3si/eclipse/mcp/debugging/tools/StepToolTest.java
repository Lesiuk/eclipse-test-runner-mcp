package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IStackFrame;
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

class StepToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private StepTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new StepTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsStep() {
        assertEquals("step", tool.getName());
    }

    @Test
    void missingActionThrows() {
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("action"));
    }

    @Test
    void invalidActionThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "jump");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("jump"));
        assertTrue(ex.getMessage().contains("over"));
    }

    @Test
    void notSuspendedThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("not suspended"));
    }

    @Test
    void stepOverSuccess() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.App");
        when(frame.getMethodName()).thenReturn("run");
        when(frame.getLineNumber()).thenReturn(15);
        when(frame.getSourceName()).thenReturn("App.java");
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        DebugPlugin plugin = mock(DebugPlugin.class);

        // Capture the listener so we can fire the event
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            // When stepOver is called, fire the SUSPEND event via the captured listener
            doAnswer(invocation -> {
                // Simulate step complete by firing event
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                IDebugEventSetListener listener = listenerCaptor.getValue();
                listener.handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                return null;
            }).when(thread).stepOver();

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");

            JsonObject result = executeAndSerialize(args);
            assertEquals("over", result.get("action").getAsString());
            assertEquals("main", result.get("thread").getAsString());

            JsonObject location = result.getAsJsonObject("location");
            assertEquals("com.example.App", location.get("class").getAsString());
            assertEquals("run", location.get("method").getAsString());
            assertEquals(15, location.get("line").getAsInt());

            verify(thread).stepOver();
        }
    }

    @Test
    void stepIntoCallsStepInto() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                listenerCaptor.getValue().handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                return null;
            }).when(thread).stepInto();

            JsonObject args = new JsonObject();
            args.addProperty("action", "into");

            tool.execute(new Args(args));
            verify(thread).stepInto();
        }
    }

    @Test
    void stepReturnCallsStepReturn() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                listenerCaptor.getValue().handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                return null;
            }).when(thread).stepReturn();

            JsonObject args = new JsonObject();
            args.addProperty("action", "return");

            tool.execute(new Args(args));
            verify(thread).stepReturn();
        }
    }

    @Test
    void terminationDuringStep() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(true);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                listenerCaptor.getValue().handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.TERMINATE)
                });
                return null;
            }).when(thread).stepOver();

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");

            JsonObject result = executeAndSerialize(args);
            assertTrue(result.get("terminated").getAsBoolean());
            assertTrue(result.get("reason").getAsString().contains("terminated"));
        }
    }

    @Test
    void stepWithBreakpointHit() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.Other");
        when(frame.getMethodName()).thenReturn("handle");
        when(frame.getLineNumber()).thenReturn(99);
        when(frame.getSourceName()).thenReturn("Other.java");
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            // Step over hits a breakpoint instead of step end
            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                listenerCaptor.getValue().handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.BREAKPOINT)
                });
                return null;
            }).when(thread).stepOver();

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");

            JsonObject result = executeAndSerialize(args);
            // Should succeed even though it was a breakpoint suspend, not step end
            assertEquals("over", result.get("action").getAsString());
            assertNotNull(result.getAsJsonObject("location"));
        }
    }

    @Test
    void debugPluginNullThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(null);

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args)));
            assertTrue(ex.getMessage().contains("Debug plugin"));
        }
    }

    @Test
    void listenerRemovedAfterStep() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                listenerCaptor.getValue().handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                return null;
            }).when(thread).stepOver();

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");

            tool.execute(new Args(args));

            // Verify listener is removed after step completes
            verify(plugin).removeDebugEventListener(listenerCaptor.getValue());
        }
    }

    @Test
    void withThreadId() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("worker");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(55L)).thenReturn(thread);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                listenerCaptor.getValue().handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                return null;
            }).when(thread).stepOver();

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");
            args.addProperty("thread_id", "55");

            JsonObject result = executeAndSerialize(args);
            assertEquals("worker", result.get("thread").getAsString());
            verify(debugContext).resolveThread(55L);
        }
    }

    @Test
    void eventsFromOtherThreadsIgnored() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaThread otherThread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(thread.isTerminated()).thenReturn(false);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        DebugPlugin plugin = mock(DebugPlugin.class);
        ArgumentCaptor<IDebugEventSetListener> listenerCaptor = ArgumentCaptor.forClass(IDebugEventSetListener.class);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(plugin);

            doAnswer(invocation -> {
                verify(plugin).addDebugEventListener(listenerCaptor.capture());
                IDebugEventSetListener listener = listenerCaptor.getValue();
                // Fire event from different thread first — should be ignored
                listener.handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(otherThread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                // Then fire the real event
                listener.handleDebugEvents(new DebugEvent[]{
                        new DebugEvent(thread, DebugEvent.SUSPEND, DebugEvent.STEP_END)
                });
                return null;
            }).when(thread).stepOver();

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");

            // Should succeed — the correct thread's event is processed
            JsonObject result = executeAndSerialize(args);
            assertEquals("main", result.get("thread").getAsString());
        }
    }
}
