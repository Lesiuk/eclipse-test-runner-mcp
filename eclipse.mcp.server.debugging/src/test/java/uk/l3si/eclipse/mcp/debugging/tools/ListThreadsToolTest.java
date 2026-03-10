package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListThreadsToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private ListThreadsTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new ListThreadsTool(debugContext);
    }

    private JsonObject executeAndSerialize() throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(null))).getAsJsonObject();
    }

    @Test
    void nameIsListThreads() {
        assertEquals("list_threads", tool.getName());
    }

    @Test
    void noActiveSessionThrows() {
        when(debugContext.getCurrentTarget()).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(null)));
        assertTrue(ex.getMessage().contains("No active debug session"));
    }

    @Test
    void terminatedTargetThrows() {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(true);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(null)));
        assertTrue(ex.getMessage().contains("No active debug session"));
    }

    @Test
    void multipleThreadsInDifferentStates() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        // Running thread
        IJavaThread running = mock(IJavaThread.class);
        when(running.getName()).thenReturn("pool-1");
        when(running.isSuspended()).thenReturn(false);
        when(running.isTerminated()).thenReturn(false);
        IJavaObject runObj = mock(IJavaObject.class);
        when(runObj.getUniqueId()).thenReturn(1L);
        when(running.getThreadObject()).thenReturn(runObj);

        // Suspended thread
        IJavaThread suspended = mock(IJavaThread.class);
        when(suspended.getName()).thenReturn("main");
        when(suspended.isSuspended()).thenReturn(true);
        when(suspended.isTerminated()).thenReturn(false);
        IJavaObject susObj = mock(IJavaObject.class);
        when(susObj.getUniqueId()).thenReturn(2L);
        when(suspended.getThreadObject()).thenReturn(susObj);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.App");
        when(frame.getMethodName()).thenReturn("main");
        when(frame.getLineNumber()).thenReturn(15);
        when(suspended.getStackFrames()).thenReturn(new IStackFrame[]{frame});

        // Terminated thread
        IJavaThread terminated = mock(IJavaThread.class);
        when(terminated.getName()).thenReturn("worker");
        when(terminated.isTerminated()).thenReturn(true);
        when(terminated.isSuspended()).thenReturn(false);
        IJavaObject termObj = mock(IJavaObject.class);
        when(termObj.getUniqueId()).thenReturn(3L);
        when(terminated.getThreadObject()).thenReturn(termObj);

        when(target.getThreads()).thenReturn(new IThread[]{running, suspended, terminated});

        JsonObject result = executeAndSerialize();
        JsonArray threads = result.getAsJsonArray("threads");
        assertEquals(3, threads.size());

        JsonObject t0 = threads.get(0).getAsJsonObject();
        assertEquals("pool-1", t0.get("name").getAsString());
        assertEquals("running", t0.get("state").getAsString());

        JsonObject t1 = threads.get(1).getAsJsonObject();
        assertEquals("main", t1.get("name").getAsString());
        assertEquals("suspended", t1.get("state").getAsString());
        assertEquals("com.example.App.main:15", t1.get("location").getAsString());

        JsonObject t2 = threads.get(2).getAsJsonObject();
        assertEquals("worker", t2.get("name").getAsString());
        assertEquals("terminated", t2.get("state").getAsString());
    }

    @Test
    void nonJavaThreadsAreSkipped() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);

        IThread nonJavaThread = mock(IThread.class);
        when(target.getThreads()).thenReturn(new IThread[]{nonJavaThread});

        JsonObject result = executeAndSerialize();
        JsonArray threads = result.getAsJsonArray("threads");
        assertEquals(0, threads.size());
    }

    @Test
    void emptyThreadList() throws Exception {
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        when(target.isTerminated()).thenReturn(false);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getThreads()).thenReturn(new IThread[]{});

        JsonObject result = executeAndSerialize();
        JsonArray threads = result.getAsJsonArray("threads");
        assertEquals(0, threads.size());
    }
}
