package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetStackTraceToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private GetStackTraceTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new GetStackTraceTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsGetStackTrace() {
        assertEquals("get_stack_trace", tool.getName());
    }

    @Test
    void suspendedThreadWithMultipleFrames() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame0 = mock(IJavaStackFrame.class);
        when(frame0.getDeclaringTypeName()).thenReturn("com.example.Service");
        when(frame0.getMethodName()).thenReturn("process");
        when(frame0.getLineNumber()).thenReturn(42);
        when(frame0.getSourceName()).thenReturn("Service.java");

        IJavaStackFrame frame1 = mock(IJavaStackFrame.class);
        when(frame1.getDeclaringTypeName()).thenReturn("com.example.App");
        when(frame1.getMethodName()).thenReturn("main");
        when(frame1.getLineNumber()).thenReturn(10);
        when(frame1.getSourceName()).thenReturn("App.java");

        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame0, frame1});

        JsonObject result = executeAndSerialize(null);
        assertEquals("main", result.get("thread").getAsString());

        JsonArray frames = result.getAsJsonArray("frames");
        assertEquals(2, frames.size());

        JsonObject f0 = frames.get(0).getAsJsonObject();
        assertEquals(0, f0.get("index").getAsInt());
        assertEquals("com.example.Service", f0.get("class").getAsString());
        assertEquals("process", f0.get("method").getAsString());
        assertEquals(42, f0.get("line").getAsInt());
        assertEquals("Service.java", f0.get("sourceName").getAsString());

        JsonObject f1 = frames.get(1).getAsJsonObject();
        assertEquals(1, f1.get("index").getAsInt());
        assertEquals("com.example.App", f1.get("class").getAsString());
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
    void withThreadId() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("worker");
        when(debugContext.resolveThread(42L)).thenReturn(thread);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{});

        JsonObject args = new JsonObject();
        args.addProperty("thread_id", "42");

        JsonObject result = executeAndSerialize(args);
        assertEquals("worker", result.get("thread").getAsString());
        verify(debugContext).resolveThread(42L);
    }

    @Test
    void nonJavaFramesAreSkipped() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IStackFrame nonJavaFrame = mock(IStackFrame.class);
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{nonJavaFrame});

        JsonObject result = executeAndSerialize(null);
        JsonArray frames = result.getAsJsonArray("frames");
        assertEquals(0, frames.size());
    }

    @Test
    void sourceNameExceptionHandled() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.Gen");
        when(frame.getMethodName()).thenReturn("run");
        when(frame.getLineNumber()).thenReturn(1);
        when(frame.getSourceName()).thenThrow(new RuntimeException("no source"));
        when(thread.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        // Should not throw — sourceName exception is caught
        JsonObject result = executeAndSerialize(null);
        JsonArray frames = result.getAsJsonArray("frames");
        assertEquals(1, frames.size());
        assertFalse(frames.get(0).getAsJsonObject().has("sourceName"));
    }
}
