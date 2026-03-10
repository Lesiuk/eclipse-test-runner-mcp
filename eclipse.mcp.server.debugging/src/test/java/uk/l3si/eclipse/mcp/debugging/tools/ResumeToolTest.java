package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.eclipse.jdt.debug.core.IJavaThread;
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
    void successfulResume() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        JsonObject result = executeAndSerialize(null);
        assertTrue(result.get("resumed").getAsBoolean());
        assertEquals("main", result.get("thread").getAsString());
        verify(thread).resume();
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
        when(thread.getName()).thenReturn("worker-1");
        when(debugContext.resolveThread(123L)).thenReturn(thread);

        JsonObject args = new JsonObject();
        args.addProperty("thread_id", "123");

        JsonObject result = executeAndSerialize(args);
        assertTrue(result.get("resumed").getAsBoolean());
        assertEquals("worker-1", result.get("thread").getAsString());
        verify(debugContext).resolveThread(123L);
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

    @Test
    void resumeCallsThreadResume() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        tool.execute(new Args(null));

        verify(thread, times(1)).resume();
    }
}
