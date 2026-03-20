package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.VariableCollector;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;
import uk.l3si.eclipse.mcp.tools.Args;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListVariablesToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private ListVariablesTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new ListVariablesTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    @Test
    void nameIsListVariables() {
        assertEquals("list_variables", tool.getName());
    }

    @Test
    void threadNotSuspendedThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        JsonObject args = new JsonObject();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("not suspended"));
    }

    @Test
    void variablesCollectedSuccessfully() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.App");
        when(frame.getMethodName()).thenReturn("run");
        when(frame.getLineNumber()).thenReturn(42);

        List<VariableResult> variables = List.of(
                VariableResult.builder().name("x").type("int").value("10").build(),
                VariableResult.builder().name("name").type("java.lang.String").value("hello").build());

        try (MockedStatic<VariableCollector> mocked = mockStatic(VariableCollector.class)) {
            mocked.when(() -> VariableCollector.collect(frame, debugContext)).thenReturn(variables);

            JsonObject args = new JsonObject();
            JsonObject result = executeAndSerialize(args);

            assertEquals("com.example.App.run:42", result.get("frame").getAsString());

            JsonArray vars = result.getAsJsonArray("variables");
            assertEquals(2, vars.size());
            assertEquals("x", vars.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("int", vars.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("10", vars.get(0).getAsJsonObject().get("value").getAsString());
            assertEquals("name", vars.get(1).getAsJsonObject().get("name").getAsString());
            assertEquals("hello", vars.get(1).getAsJsonObject().get("value").getAsString());
        }
    }

    @Test
    void nullVariablesThrowsInvalidFrame() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);

        try (MockedStatic<VariableCollector> mocked = mockStatic(VariableCollector.class)) {
            mocked.when(() -> VariableCollector.collect(frame, debugContext)).thenReturn(null);

            JsonObject args = new JsonObject();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("no longer valid"));
        }
    }

    @Test
    void customThreadIdAndFrameIndex() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(debugContext.resolveThread(77L)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveFrame(thread, 3)).thenReturn(frame);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.Service");
        when(frame.getMethodName()).thenReturn("handle");
        when(frame.getLineNumber()).thenReturn(15);

        try (MockedStatic<VariableCollector> mocked = mockStatic(VariableCollector.class)) {
            mocked.when(() -> VariableCollector.collect(frame, debugContext)).thenReturn(List.of());

            JsonObject args = new JsonObject();
            args.addProperty("thread_id", "77");
            args.addProperty("frame_index", "3");

            JsonObject result = executeAndSerialize(args);

            verify(debugContext).resolveThread(77L);
            verify(debugContext).resolveFrame(thread, 3);
            assertEquals("com.example.Service.handle:15", result.get("frame").getAsString());
        }
    }

    @Test
    void defaultsThreadAndFrame() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(frame.getDeclaringTypeName()).thenReturn("com.example.Main");
        when(frame.getMethodName()).thenReturn("main");
        when(frame.getLineNumber()).thenReturn(1);

        try (MockedStatic<VariableCollector> mocked = mockStatic(VariableCollector.class)) {
            mocked.when(() -> VariableCollector.collect(frame, debugContext)).thenReturn(List.of());

            JsonObject args = new JsonObject();
            executeAndSerialize(args);

            verify(debugContext).resolveThread(null);
            verify(debugContext).resolveFrame(thread, null);
        }
    }
}
