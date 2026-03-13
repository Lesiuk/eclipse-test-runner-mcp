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

class UpdateFlowToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private UpdateFlowTool tool;

    @BeforeEach
    void setUp() {
        tool = new UpdateFlowTool();
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
    void nameIsUpdateFlow() {
        assertEquals("bpmn2_update_flow", tool.getName());
    }

    @Test
    void updateName() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");
        args.addProperty("name", "Main Path");

        JsonObject result = executeAndSerialize(args);

        assertEquals("SequenceFlow_1", result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("name"));

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById("SequenceFlow_1");
        assertEquals("Main Path", flow.getAttribute("name"));
    }

    @Test
    void updateConditionAddNew() throws Exception {
        Path file = copyTestResource();
        // SequenceFlow_1 has no conditionExpression

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");
        args.addProperty("condition", "return true;");

        JsonObject result = executeAndSerialize(args);

        assertTrue(result.get("updated").getAsJsonArray().toString().contains("condition"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById("SequenceFlow_1");
        Element condExpr = findChildElement(flow,
                Bpmn2Document.NS_BPMN2, "conditionExpression");
        assertNotNull(condExpr, "conditionExpression should be created");
        assertEquals("return true;", condExpr.getTextContent());
        assertEquals("bpmn2:tFormalExpression",
                condExpr.getAttributeNS(Bpmn2Document.NS_XSI, "type"));
    }

    @Test
    void updateConditionModifyExisting() throws Exception {
        Path file = copyTestResource();

        // First, add a flow with a condition using AddFlowTool
        addExtraNodesAndFlowWithCondition(file);

        // Now update the condition on the flow we just added
        // We need to find the flow ID; let's re-parse
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flowWithCondition = null;
        for (Element flow : doc.listFlows()) {
            Element condExpr = findChildElement(flow,
                    Bpmn2Document.NS_BPMN2, "conditionExpression");
            if (condExpr != null) {
                flowWithCondition = flow;
                break;
            }
        }
        assertNotNull(flowWithCondition, "Should have a flow with condition");
        String flowId = flowWithCondition.getAttribute("id");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", flowId);
        args.addProperty("condition", "return false;");

        executeAndSerialize(args);

        // Verify the condition was updated (not a new one created)
        doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById(flowId);
        Element condExpr = findChildElement(flow,
                Bpmn2Document.NS_BPMN2, "conditionExpression");
        assertNotNull(condExpr);
        assertEquals("return false;", condExpr.getTextContent());

        // Verify only one conditionExpression exists (not duplicated)
        int condCount = 0;
        NodeList children = flow.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && "conditionExpression".equals(el.getLocalName())) {
                condCount++;
            }
        }
        assertEquals(1, condCount, "Should have exactly one conditionExpression");
    }

    @Test
    void updatePriority() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");
        args.addProperty("priority", "10");

        JsonObject result = executeAndSerialize(args);

        assertTrue(result.get("updated").getAsJsonArray().toString().contains("priority"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById("SequenceFlow_1");
        assertEquals("10", flow.getAttributeNS(Bpmn2Document.NS_TNS, "priority"));
    }

    @Test
    void noPropertiesToUpdateThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("No properties to update"), ex.getMessage());
    }

    @Test
    void flowNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "SequenceFlow_999");
        args.addProperty("name", "test");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Sequence flow not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("SequenceFlow_999"), ex.getMessage());
    }

    // ---- Helpers ----

    private void addExtraNodesAndFlowWithCondition(Path file) throws Exception {
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
        flowArgs.addProperty("source", "Task_1");
        flowArgs.addProperty("target", "Task_2");
        flowArgs.addProperty("condition", "return x > 0;");
        addFlowTool.execute(new Args(flowArgs));
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
