package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.debugging.model.RemoveBreakpointResult;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoveBreakpointToolTest {

    private static final Gson GSON = new Gson();

    private BreakpointManager breakpointManager;
    private RemoveBreakpointTool tool;

    @BeforeEach
    void setUp() {
        breakpointManager = mock(BreakpointManager.class);
        tool = new RemoveBreakpointTool(breakpointManager);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsRemoveBreakpoint() {
        assertEquals("remove_breakpoint", tool.getName());
    }

    @Test
    void successfulRemoval() throws Exception {
        when(breakpointManager.removeBreakpoint(42L))
                .thenReturn(RemoveBreakpointResult.builder()
                        .removed(true)
                        .id(42L)
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("id", "42");

        JsonObject result = executeAndSerialize(args);
        assertTrue(result.get("removed").getAsBoolean());
        assertEquals(42L, result.get("id").getAsLong());
    }

    @Test
    void missingIdThrowsIllegalArgument() {
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("id"));
    }

    @Test
    void nonNumericIdThrowsIllegalArgument() {
        JsonObject args = new JsonObject();
        args.addProperty("id", "not-a-number");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("integer"));
    }

    @Test
    void notFoundPropagatesFromManager() throws Exception {
        when(breakpointManager.removeBreakpoint(999L))
                .thenThrow(new IllegalArgumentException(
                        "Breakpoint not found with ID: 999. Use 'list_breakpoints' to see all current breakpoints and their IDs."));

        JsonObject args = new JsonObject();
        args.addProperty("id", "999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("999"));
        assertTrue(ex.getMessage().contains("list_breakpoints"));
    }

    @Test
    void delegatesToBreakpointManager() throws Exception {
        when(breakpointManager.removeBreakpoint(anyLong()))
                .thenReturn(RemoveBreakpointResult.builder().removed(true).id(7L).build());

        JsonObject args = new JsonObject();
        args.addProperty("id", "7");

        tool.execute(new Args(args));
        verify(breakpointManager).removeBreakpoint(7L);
    }
}
