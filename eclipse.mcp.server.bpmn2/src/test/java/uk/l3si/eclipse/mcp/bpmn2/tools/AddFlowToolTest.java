package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
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

class AddFlowToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddFlowTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddFlowTool();
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

    /**
     * Adds extra nodes (a second task and an exclusive gateway) to the test file
     * so we can create flows between them in tests.
     */
    private void addExtraNodes(Path file) throws Exception {
        AddNodeTool addNodeTool = new AddNodeTool();

        // Add Task_2
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("file", file.toString());
        taskArgs.addProperty("type", "task");
        taskArgs.addProperty("name", "Second Task");
        taskArgs.addProperty("taskName", "com.example.IService_second");
        taskArgs.addProperty("id", "Task_2");
        addNodeTool.execute(new Args(taskArgs));

        // Add EndEvent_2
        JsonObject endArgs = new JsonObject();
        endArgs.addProperty("file", file.toString());
        endArgs.addProperty("type", "endEvent");
        endArgs.addProperty("name", "Second End");
        endArgs.addProperty("id", "EndEvent_2");
        addNodeTool.execute(new Args(endArgs));
    }

    @Test
    void nameIsAddFlow() {
        assertEquals("bpmn2_add_flow", tool.getName());
    }

    @Test
    void addFlowBetweenExistingNodes() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");

        JsonObject result = executeAndSerialize(args);

        assertNotNull(result.get("id"));
        assertEquals("Task_1", result.get("source").getAsString());
        assertEquals("Task_2", result.get("target").getAsString());

        // Re-parse and verify
        String flowId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element flow = doc.findFlowById(flowId);
        assertNotNull(flow, "Flow should exist after adding");
        assertEquals("Task_1", flow.getAttribute("sourceRef"));
        assertEquals("Task_2", flow.getAttribute("targetRef"));

        // Verify outgoing ref on source node
        Element sourceNode = doc.findNodeById("Task_1");
        assertTrue(hasFlowRef(sourceNode, "outgoing", flowId),
                "Source node should have outgoing ref to flow");

        // Verify incoming ref on target node
        Element targetNode = doc.findNodeById("Task_2");
        assertTrue(hasFlowRef(targetNode, "incoming", flowId),
                "Target node should have incoming ref to flow");
    }

    @Test
    void addFlowWithCondition() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("condition", "return x > 0;");

        JsonObject result = executeAndSerialize(args);

        String flowId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById(flowId);
        assertNotNull(flow);

        Element condExpr = findChildElement(flow,
                Bpmn2Document.NS_BPMN2, "conditionExpression");
        assertNotNull(condExpr, "conditionExpression should exist");
        assertEquals("return x > 0;", condExpr.getTextContent());
        assertEquals("bpmn2:tFormalExpression",
                condExpr.getAttributeNS(Bpmn2Document.NS_XSI, "type"));
        assertEquals("http://www.java.com/java", condExpr.getAttribute("language"));
    }

    @Test
    void addFlowWithName() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("name", "YES");

        JsonObject result = executeAndSerialize(args);

        String flowId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById(flowId);
        assertNotNull(flow);
        assertEquals("YES", flow.getAttribute("name"));
    }

    @Test
    void addFlowWithPriority() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("priority", "5");

        JsonObject result = executeAndSerialize(args);

        String flowId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById(flowId);
        assertNotNull(flow);
        assertEquals("5", flow.getAttributeNS(Bpmn2Document.NS_TNS, "priority"));
    }

    @Test
    void addFlowDefaultPriority() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");

        JsonObject result = executeAndSerialize(args);

        String flowId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById(flowId);
        assertNotNull(flow);
        assertEquals("1", flow.getAttributeNS(Bpmn2Document.NS_TNS, "priority"));
    }

    @Test
    void selfLoopThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("self-loop"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Task_1"), ex.getMessage());
    }

    @Test
    void duplicateFlowThrowsError() throws Exception {
        Path file = copyTestResource();
        // SequenceFlow_1 already connects StartEvent_1 -> Task_1

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "StartEvent_1");
        args.addProperty("target", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Duplicate flow"), ex.getMessage());
        assertTrue(ex.getMessage().contains("SequenceFlow_1"), ex.getMessage());
    }

    @Test
    void sourceIsEndEventThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "EndEvent_1");
        args.addProperty("target", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("endEvent"), ex.getMessage());
        assertTrue(ex.getMessage().contains("EndEvent_1"), ex.getMessage());
    }

    @Test
    void targetIsPlainStartEventThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "StartEvent_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("startEvent"), ex.getMessage());
        assertTrue(ex.getMessage().contains("StartEvent_1"), ex.getMessage());
    }

    @Test
    void sourceNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "NonExistent_1");
        args.addProperty("target", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NonExistent_1"), ex.getMessage());
    }

    @Test
    void targetNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", "Task_1");
        args.addProperty("target", "NonExistent_2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NonExistent_2"), ex.getMessage());
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

    private static Element findChildElement(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }
}
