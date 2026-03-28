package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.debugging.model.BreakpointInfo;
import uk.l3si.eclipse.mcp.debugging.model.BreakpointResult;
import uk.l3si.eclipse.mcp.debugging.model.ListBreakpointsResult;
import uk.l3si.eclipse.mcp.debugging.model.RemoveBreakpointResult;
import uk.l3si.eclipse.mcp.tools.Args;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BreakpointToolTest {

    private static final Gson GSON = new Gson();

    private BreakpointManager breakpointManager;
    private BreakpointTool tool;

    @BeforeEach
    void setUp() {
        breakpointManager = mock(BreakpointManager.class);
        tool = new BreakpointTool(breakpointManager);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    // --- name ---

    @Test
    void nameIsBreakpoint() {
        assertEquals("breakpoint", tool.getName());
    }

    // --- action=set ---

    @Test
    void setBreakpointSuccess() throws Exception {
        when(breakpointManager.setBreakpoint("com.example.MyService", 42, null))
                .thenReturn(BreakpointResult.builder()
                        .id(1L)
                        .line(42)
                        .enabled(true)
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "42");

        JsonObject result = executeAndSerialize(args);
        assertEquals(1L, result.get("id").getAsLong());
        assertEquals(42, result.get("line").getAsInt());
        assertTrue(result.get("enabled").getAsBoolean());
    }

    @Test
    void setBreakpointWithCondition() throws Exception {
        when(breakpointManager.setBreakpoint("com.example.MyService", 10, "x > 5"))
                .thenReturn(BreakpointResult.builder()
                        .id(2L)
                        .line(10)
                        .enabled(true)
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "10");
        args.addProperty("condition", "x > 5");

        JsonObject result = executeAndSerialize(args);
        assertEquals(2L, result.get("id").getAsLong());
    }

    @Test
    void setMissingClassThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("line", "42");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("class"));
    }

    @Test
    void setBlankClassThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "  ");
        args.addProperty("line", "42");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("class"));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void setMissingLineThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "com.example.MyService");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("line"));
    }

    @Test
    void setZeroLineThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "0");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("positive"));
    }

    @Test
    void setDelegatesToBreakpointManager() throws Exception {
        when(breakpointManager.setBreakpoint(anyString(), anyInt(), any()))
                .thenReturn(BreakpointResult.builder()
                        .id(1L).line(1).enabled(true).build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "com.example.Foo");
        args.addProperty("line", "99");
        args.addProperty("condition", "i == 0");

        tool.execute(new Args(args), message -> {});
        verify(breakpointManager).setBreakpoint("com.example.Foo", 99, "i == 0");
    }

    @Test
    void setDuplicateBreakpointThrows() throws Exception {
        when(breakpointManager.setBreakpoint("com.example.MyService", 42, null))
                .thenThrow(new IllegalArgumentException(
                        "A breakpoint already exists at com.example.MyService:42 (ID: 1). "
                        + "Use breakpoint action='remove' to remove it first, or action='list' to see all breakpoints."));

        JsonObject args = new JsonObject();
        args.addProperty("action", "set");
        args.addProperty("class", "com.example.MyService");
        args.addProperty("line", "42");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("already exists"));
        assertTrue(ex.getMessage().contains("MyService:42"));
    }

    // --- action=remove ---

    @Test
    void removeSuccess() throws Exception {
        when(breakpointManager.removeBreakpoint(42L))
                .thenReturn(RemoveBreakpointResult.builder()
                        .removed(true)
                        .id(42L)
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "remove");
        args.addProperty("id", "42");

        JsonObject result = executeAndSerialize(args);
        assertTrue(result.get("removed").getAsBoolean());
        assertEquals(42L, result.get("id").getAsLong());
    }

    @Test
    void removeMissingIdThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "remove");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    void removeDelegatesToBreakpointManager() throws Exception {
        when(breakpointManager.removeBreakpoint(anyLong()))
                .thenReturn(RemoveBreakpointResult.builder().removed(true).id(7L).build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "remove");
        args.addProperty("id", "7");

        tool.execute(new Args(args), message -> {});
        verify(breakpointManager).removeBreakpoint(7L);
    }

    // --- action=list ---

    @Test
    void listEmpty() throws Exception {
        when(breakpointManager.listBreakpoints())
                .thenReturn(ListBreakpointsResult.builder()
                        .breakpoints(Collections.emptyList())
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "list");

        JsonObject result = executeAndSerialize(args);
        JsonArray breakpoints = result.getAsJsonArray("breakpoints");
        assertEquals(0, breakpoints.size());
    }

    @Test
    void listMultiple() throws Exception {
        when(breakpointManager.listBreakpoints())
                .thenReturn(ListBreakpointsResult.builder()
                        .breakpoints(List.of(
                                BreakpointInfo.builder()
                                        .id(1L)
                                        .className("com.example.Foo")
                                        .line(10)
                                        .enabled(true)
                                        .build(),
                                BreakpointInfo.builder()
                                        .id(2L)
                                        .className("com.example.Bar")
                                        .line(20)
                                        .condition("x > 0")
                                        .enabled(false)
                                        .build()
                        ))
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "list");

        JsonObject result = executeAndSerialize(args);
        JsonArray breakpoints = result.getAsJsonArray("breakpoints");
        assertEquals(2, breakpoints.size());

        JsonObject bp1 = breakpoints.get(0).getAsJsonObject();
        assertEquals(1L, bp1.get("id").getAsLong());
        assertEquals("com.example.Foo", bp1.get("class").getAsString());
        assertEquals(10, bp1.get("line").getAsInt());
        assertTrue(bp1.get("enabled").getAsBoolean());

        JsonObject bp2 = breakpoints.get(1).getAsJsonObject();
        assertEquals(2L, bp2.get("id").getAsLong());
        assertEquals("x > 0", bp2.get("condition").getAsString());
    }

    @Test
    void listDelegatesToBreakpointManager() throws Exception {
        when(breakpointManager.listBreakpoints())
                .thenReturn(ListBreakpointsResult.builder()
                        .breakpoints(Collections.emptyList())
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "list");

        tool.execute(new Args(args), message -> {});
        verify(breakpointManager).listBreakpoints();
    }

    // --- action=clear ---

    @Test
    void clearRemovesAll() throws Exception {
        when(breakpointManager.clearBreakpoints()).thenReturn(3);

        JsonObject args = new JsonObject();
        args.addProperty("action", "clear");

        JsonObject result = executeAndSerialize(args);
        assertEquals(3, result.get("removed").getAsInt());
        verify(breakpointManager).clearBreakpoints();
    }

    @Test
    void clearWithNoneReturnsZero() throws Exception {
        when(breakpointManager.clearBreakpoints()).thenReturn(0);

        JsonObject args = new JsonObject();
        args.addProperty("action", "clear");

        JsonObject result = executeAndSerialize(args);
        assertEquals(0, result.get("removed").getAsInt());
    }

    // --- invalid/missing action ---

    @Test
    void invalidActionThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "toggle");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Invalid breakpoint action"));
        assertTrue(ex.getMessage().contains("toggle"));
    }

    @Test
    void missingActionThrows() {
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("action"));
    }
}
