package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoveNodeToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private RemoveNodeTool tool;

    @BeforeEach
    void setUp() {
        tool = new RemoveNodeTool();
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
    void nameIsRemoveNode() {
        assertEquals("bpmn2_remove_node", tool.getName());
    }

    // ---- Remove Task_1 ----

    @Test
    void removeTaskRemovesNodeAndConnectedFlows() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_1");

        JsonObject result = executeAndSerialize(args);

        assertEquals("Task_1", result.get("id").getAsString());

        JsonArray removedFlows = result.get("removedFlows").getAsJsonArray();
        List<String> removedFlowIds = new ArrayList<>();
        removedFlows.forEach(e -> removedFlowIds.add(e.getAsString()));
        assertTrue(removedFlowIds.contains("SequenceFlow_1"),
                "SequenceFlow_1 should be removed");
        assertTrue(removedFlowIds.contains("SequenceFlow_2"),
                "SequenceFlow_2 should be removed");

        // Re-parse and verify node is gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNull(doc.findNodeById("Task_1"), "Task_1 should be removed");

        // Verify flows are gone
        assertNull(doc.findFlowById("SequenceFlow_1"),
                "SequenceFlow_1 should be removed");
        assertNull(doc.findFlowById("SequenceFlow_2"),
                "SequenceFlow_2 should be removed");

        // Verify StartEvent_1 no longer has outgoing ref to SequenceFlow_1
        Element startEvent = doc.findNodeById("StartEvent_1");
        assertNotNull(startEvent);
        assertFalse(hasFlowRef(startEvent, "outgoing", "SequenceFlow_1"),
                "StartEvent_1 should not have outgoing ref to SequenceFlow_1");

        // Verify EndEvent_1 no longer has incoming ref to SequenceFlow_2
        Element endEvent = doc.findNodeById("EndEvent_1");
        assertNotNull(endEvent);
        assertFalse(hasFlowRef(endEvent, "incoming", "SequenceFlow_2"),
                "EndEvent_1 should not have incoming ref to SequenceFlow_2");
    }

    @Test
    void removeTaskCleansDiagramElements() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_1");

        executeAndSerialize(args);

        // Re-parse and verify diagram elements are cleaned
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element diagramPlane = doc.getDiagramPlane();
        assertNotNull(diagramPlane);

        // BPMNShape for Task_1 should be gone
        assertFalse(hasDiagramElement(diagramPlane, "Task_1"),
                "BPMNShape for Task_1 should be removed");
    }

    // ---- Error: removing the only startEvent ----

    @Test
    void removeOnlyStartEventThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "StartEvent_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Cannot remove the only startEvent"),
                ex.getMessage());
    }

    // ---- Error: node not found ----

    @Test
    void nodeNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
    }

    // ---- Helpers ----

    private boolean hasFlowRef(Element node, String refType, String flowId) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && refType.equals(el.getLocalName())
                    && flowId.equals(el.getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDiagramElement(Element diagramPlane, String bpmnElementId) {
        NodeList children = diagramPlane.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && bpmnElementId.equals(el.getAttribute("bpmnElement"))) {
                return true;
            }
        }
        return false;
    }
}
