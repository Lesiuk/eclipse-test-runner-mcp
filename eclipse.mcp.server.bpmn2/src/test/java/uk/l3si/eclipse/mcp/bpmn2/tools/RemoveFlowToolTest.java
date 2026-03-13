package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RemoveFlowToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private RemoveFlowTool tool;

    @BeforeEach
    void setUp() {
        tool = new RemoveFlowTool();
    }

    private Path copyTestResource() throws Exception {
        Path target = tempDir.resolve("test-flow.bpmn2");
        try (InputStream in = getClass().getResourceAsStream("/test-flow.bpmn2")) {
            assertNotNull(in, "Test resource not found");
            Files.copy(in, target);
        }
        return target;
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsRemoveFlow() {
        assertEquals("bpmn2_remove_flow", tool.getName());
    }

    @Test
    void removeFlow() throws Exception {
        Path file = copyTestResource();
        // SequenceFlow_1 connects StartEvent_1 -> Task_1

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");

        JsonObject result = executeAndSerialize(args);

        assertEquals("SequenceFlow_1", result.get("id").getAsString());

        // Re-parse and verify flow is gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNull(doc.findFlowById("SequenceFlow_1"), "Flow should be removed");

        // Verify outgoing ref cleaned from source
        Element startEvent = doc.findNodeById("StartEvent_1");
        assertFalse(hasFlowRef(startEvent, "outgoing", "SequenceFlow_1"),
                "Outgoing ref should be removed from source");

        // Verify incoming ref cleaned from target
        Element task = doc.findNodeById("Task_1");
        assertFalse(hasFlowRef(task, "incoming", "SequenceFlow_1"),
                "Incoming ref should be removed from target");
    }

    @Test
    void removeFlowWarningsWhenOrphanedNodes() throws Exception {
        Path file = copyTestResource();
        // SequenceFlow_1 connects StartEvent_1 -> Task_1
        // After removing it, StartEvent_1 (not endEvent) has no outgoing flows
        // and Task_1 (not startEvent) has no incoming flows (only SequenceFlow_2 outgoing remains)

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");

        JsonObject result = executeAndSerialize(args);

        JsonArray warnings = result.get("warnings").getAsJsonArray();
        assertFalse(warnings.isEmpty(), "Should have warnings about orphaned nodes");

        String warningsStr = warnings.toString();
        assertTrue(warningsStr.contains("StartEvent_1"),
                "Should warn about StartEvent_1 having no outgoing flows");
        assertTrue(warningsStr.contains("Task_1"),
                "Should warn about Task_1 having no incoming flows");
    }

    @Test
    void removeFlowNoWarningsWhenNotOrphaned() throws Exception {
        Path file = copyTestResource();

        // Add an extra flow from StartEvent_1 -> EndEvent_1 so removing
        // SequenceFlow_1 doesn't leave StartEvent_1 without outgoing flows
        // (it will still have the new flow) and Task_1 still has SequenceFlow_2 outgoing
        // but will lose its only incoming flow
        AddNodeTool addNodeTool = new AddNodeTool();
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("file", file.toString());
        taskArgs.addProperty("type", "task");
        taskArgs.addProperty("name", "Extra Task");
        taskArgs.addProperty("taskName", "com.example.IService_extra");
        taskArgs.addProperty("id", "Task_2");
        addNodeTool.execute(new Args(taskArgs));

        AddFlowTool addFlowTool = new AddFlowTool();
        JsonObject flowArgs = new JsonObject();
        flowArgs.addProperty("file", file.toString());
        flowArgs.addProperty("source", "StartEvent_1");
        flowArgs.addProperty("target", "Task_2");
        addFlowTool.execute(new Args(flowArgs));

        // Now remove SequenceFlow_1 (StartEvent_1 -> Task_1)
        // StartEvent_1 still has the new flow (outgoing), so no warning for it
        // Task_1 loses its only incoming flow, so warning expected
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");

        JsonObject result = executeAndSerialize(args);

        JsonArray warnings = result.get("warnings").getAsJsonArray();
        String warningsStr = warnings.toString();
        assertFalse(warningsStr.contains("StartEvent_1"),
                "StartEvent_1 still has outgoing flow, no warning expected");
        assertTrue(warningsStr.contains("Task_1"),
                "Task_1 lost its only incoming flow, warning expected");
    }

    @Test
    void flowNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Sequence flow not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("SequenceFlow_999"), ex.getMessage());
    }

    // ---- Helpers ----

    private static boolean hasFlowRef(Element node, String refType, String flowId) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && refType.equals(el.getLocalName())
                    && flowId.equals(el.getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }
}
