package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.debugging.model.BreakpointResult;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SetBreakpointToolTest {

    private static final Gson GSON = new Gson();

    private BreakpointManager breakpointManager;
    private SetBreakpointTool tool;

    @BeforeEach
    void setUp() {
        breakpointManager = mock(BreakpointManager.class);
        tool = new SetBreakpointTool(breakpointManager);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsSetBreakpoint() {
        assertEquals("set_breakpoint", tool.getName());
    }

    @Test
    void successfulBreakpointCreation() throws Exception {
        when(breakpointManager.setBreakpoint("com.example.MyService", 42, null))
                .thenReturn(BreakpointResult.builder()
                        .id(1L)
                        .className("com.example.MyService")
                        .line(42)
                        .enabled(true)
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "42");

        JsonObject result = executeAndSerialize(args);
        assertEquals(1L, result.get("id").getAsLong());
        assertEquals("com.example.MyService", result.get("class").getAsString());
        assertEquals(42, result.get("line").getAsInt());
        assertTrue(result.get("enabled").getAsBoolean());
    }

    @Test
    void breakpointWithCondition() throws Exception {
        when(breakpointManager.setBreakpoint("com.example.MyService", 10, "x > 5"))
                .thenReturn(BreakpointResult.builder()
                        .id(2L)
                        .className("com.example.MyService")
                        .line(10)
                        .condition("x > 5")
                        .enabled(true)
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "10");
        args.addProperty("condition", "x > 5");

        JsonObject result = executeAndSerialize(args);
        assertEquals("x > 5", result.get("condition").getAsString());
    }

    @Test
    void missingClassThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("line", "42");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("class"));
    }

    @Test
    void blankClassThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("class", "  ");
        args.addProperty("line", "42");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("class"));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void missingLineThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.MyService");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("line"));
    }

    @Test
    void zeroLineThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "0");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void negativeLineThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "-5");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void nonNumericLineThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "abc");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("integer"));
    }

    @Test
    void delegatesToBreakpointManager() throws Exception {
        when(breakpointManager.setBreakpoint(anyString(), anyInt(), any()))
                .thenReturn(BreakpointResult.builder()
                        .id(1L).className("A").line(1).enabled(true).build());

        JsonObject args = new JsonObject();
        args.addProperty("class", "com.example.Foo");
        args.addProperty("line", "99");
        args.addProperty("condition", "i == 0");

        tool.execute(new Args(args));
        verify(breakpointManager).setBreakpoint("com.example.Foo", 99, "i == 0");
    }
}
