package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
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
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getCurrentLocation()).thenReturn(
                LocationInfo.builder()
                        .className("com.example.App")
                        .method("run")
                        .line(15)
                        .sourceName("App.java")
                        .build());

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

    @Test
    void stepIntoCallsStepInto() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);

        JsonObject args = new JsonObject();
        args.addProperty("action", "into");

        tool.execute(new Args(args));
        verify(thread).stepInto();
    }

    @Test
    void stepReturnCallsStepReturn() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);

        JsonObject args = new JsonObject();
        args.addProperty("action", "return");

        tool.execute(new Args(args));
        verify(thread).stepReturn();
    }

    @Test
    void terminationDuringStep() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TERMINATED);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertTrue(result.get("terminated").getAsBoolean());
        assertTrue(result.get("reason").getAsString().contains("terminated"));
    }

    @Test
    void stepTimeout() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TIMEOUT);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertTrue(result.get("reason").getAsString().contains("timed out"));
    }

    @Test
    void stepWithBreakpointHit() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getCurrentLocation()).thenReturn(
                LocationInfo.builder()
                        .className("com.example.Other")
                        .method("handle")
                        .line(99)
                        .sourceName("Other.java")
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertEquals("over", result.get("action").getAsString());
        assertNotNull(result.getAsJsonObject("location"));
    }

    @Test
    void withThreadId() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("worker");
        when(debugContext.resolveThread(55L)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");
        args.addProperty("thread_id", "55");

        JsonObject result = executeAndSerialize(args);
        assertEquals("worker", result.get("thread").getAsString());
        verify(debugContext).resolveThread(55L);
    }
}
