package uk.l3si.eclipse.mcp.tools;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private static McpTool dummyTool(String name) {
        return new McpTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " description"; }
            @Override public InputSchema getInputSchema() {
                return InputSchema.builder().build();
            }
            @Override public Object execute(Args args) { return "ok"; }
        };
    }

    @Test
    void disabledToolHiddenFromList() {
        ToolRegistry registry = new ToolRegistry();
        McpTool tool = dummyTool("test_tool");
        registry.addTool(tool);

        registry.setDisabledTools(Set.of("test_tool"));

        List<Map<String, Object>> schemas = registry.listToolSchemas();
        boolean found = schemas.stream().anyMatch(s -> "test_tool".equals(s.get("name")));
        assertFalse(found, "disabled tool should not appear in listToolSchemas");
    }

    @Test
    void disabledToolRejectedOnCall() {
        ToolRegistry registry = new ToolRegistry();
        McpTool tool = dummyTool("test_tool");
        registry.addTool(tool);

        registry.setDisabledTools(Set.of("test_tool"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.callTool("test_tool", new JsonObject()));
    }

    @Test
    void enabledToolVisibleAndCallable() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        McpTool tool = dummyTool("test_tool");
        registry.addTool(tool);

        registry.setDisabledTools(Set.of("test_tool"));
        registry.setDisabledTools(Set.of()); // re-enable

        List<Map<String, Object>> schemas = registry.listToolSchemas();
        boolean found = schemas.stream().anyMatch(s -> "test_tool".equals(s.get("name")));
        assertTrue(found, "re-enabled tool should appear in listToolSchemas");

        Object result = registry.callTool("test_tool", new JsonObject());
        assertEquals("ok", result);
    }

    @Test
    void toolsByGroupReturnsCorrectGroups() {
        ToolRegistry registry = new ToolRegistry();
        McpTool debugTool = dummyTool("debug_tool");
        McpTool bpmnTool = dummyTool("bpmn_tool");

        registry.addTool(debugTool, "Debugging");
        registry.addTool(bpmnTool, "BPMN2");

        Map<String, List<McpTool>> groups = registry.getToolsByGroup();
        assertTrue(groups.containsKey("Core"), "should have Core group");
        assertTrue(groups.containsKey("Debugging"), "should have Debugging group");
        assertTrue(groups.containsKey("BPMN2"), "should have BPMN2 group");

        assertTrue(groups.get("Debugging").stream().anyMatch(t -> t.getName().equals("debug_tool")));
        assertTrue(groups.get("BPMN2").stream().anyMatch(t -> t.getName().equals("bpmn_tool")));
    }

    @Test
    void isToolEnabledReflectsState() {
        ToolRegistry registry = new ToolRegistry();
        McpTool tool = dummyTool("my_tool");
        registry.addTool(tool);

        assertTrue(registry.isToolEnabled("my_tool"));

        registry.setDisabledTools(Set.of("my_tool"));
        assertFalse(registry.isToolEnabled("my_tool"));

        registry.setDisabledTools(Set.of());
        assertTrue(registry.isToolEnabled("my_tool"));
    }
}
