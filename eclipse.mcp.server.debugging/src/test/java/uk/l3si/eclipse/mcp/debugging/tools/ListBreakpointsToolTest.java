package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.debugging.BreakpointManager;
import uk.l3si.eclipse.mcp.debugging.model.BreakpointInfo;
import uk.l3si.eclipse.mcp.debugging.model.ListBreakpointsResult;
import uk.l3si.eclipse.mcp.tools.Args;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ListBreakpointsToolTest {

    private static final Gson GSON = new Gson();

    private BreakpointManager breakpointManager;
    private ListBreakpointsTool tool;

    @BeforeEach
    void setUp() {
        breakpointManager = mock(BreakpointManager.class);
        tool = new ListBreakpointsTool(breakpointManager);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsListBreakpoints() {
        assertEquals("list_breakpoints", tool.getName());
    }

    @Test
    void emptyList() throws Exception {
        when(breakpointManager.listBreakpoints())
                .thenReturn(ListBreakpointsResult.builder()
                        .breakpoints(Collections.emptyList())
                        .build());

        JsonObject result = executeAndSerialize(null);
        JsonArray breakpoints = result.getAsJsonArray("breakpoints");
        assertEquals(0, breakpoints.size());
    }

    @Test
    void multipleBreakpoints() throws Exception {
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

        JsonObject result = executeAndSerialize(null);
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
    void delegatesToBreakpointManager() throws Exception {
        when(breakpointManager.listBreakpoints())
                .thenReturn(ListBreakpointsResult.builder()
                        .breakpoints(Collections.emptyList())
                        .build());

        tool.execute(new Args(null));
        verify(breakpointManager).listBreakpoints();
    }
}
