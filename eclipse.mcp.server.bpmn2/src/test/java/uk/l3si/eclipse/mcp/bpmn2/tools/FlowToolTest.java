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

class FlowToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private FlowTool tool;

    @BeforeEach
    void setUp() {
        tool = new FlowTool();
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
        // Add Task_2
        AddServiceTaskTool addServiceTaskTool = new AddServiceTaskTool();
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("file", file.toString());
        taskArgs.addProperty("name", "Second Task");
        taskArgs.addProperty("taskName", "com.example.IService_second");
        taskArgs.addProperty("id", "Task_2");
        addServiceTaskTool.execute(new Args(taskArgs));

        // Add EndEvent_2
        AddEndEventTool addEndEventTool = new AddEndEventTool();
        JsonObject endArgs = new JsonObject();
        endArgs.addProperty("file", file.toString());
        endArgs.addProperty("name", "Second End");
        endArgs.addProperty("id", "EndEvent_2");
        addEndEventTool.execute(new Args(endArgs));
    }

    // ---- Add flow tests ----

    @Test
    void nameIsFlow() {
        assertEquals("bpmn2_flow", tool.getName());
    }

    @Test
    void invalidActionThrowsError() {
        JsonObject args = new JsonObject();
        args.addProperty("file", "/dummy.bpmn2");
        args.addProperty("action", "invalid");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
        assertTrue(ex.getMessage().contains("invalid"), ex.getMessage());
    }

    @Test
    void addFlowBetweenExistingNodes() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
        args.addProperty("source", "Task_1");
        args.addProperty("target", "NonExistent_2");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NonExistent_2"), ex.getMessage());
    }

    @Test
    void negativePriorityThrowsErrorOnAdd() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("priority", "-1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("positive"), ex.getMessage());
    }

    @Test
    void zeroPriorityThrowsError() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("priority", "0");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("positive"), ex.getMessage());
    }

    @Test
    void evaluatesToTypeRefWithoutConditionThrowsError() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("evaluatesToTypeRef", "ItemDefinition_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("without a 'condition'"), ex.getMessage());
    }

    @Test
    void invalidEvaluatesToTypeRefThrowsErrorOnAdd() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("condition", "return true;");
        args.addProperty("evaluatesToTypeRef", "NonExistent_ItemDef");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("ItemDefinition not found"), ex.getMessage());
    }

    @Test
    void validEvaluatesToTypeRefWithCondition() throws Exception {
        Path file = copyTestResource();
        addExtraNodes(file);

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("source", "Task_1");
        args.addProperty("target", "Task_2");
        args.addProperty("condition", "return true;");
        args.addProperty("evaluatesToTypeRef", "ItemDefinition_1");

        JsonObject result = executeAndSerialize(args);

        String flowId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element flow = doc.findFlowById(flowId);
        Element condExpr = findChildElement(flow,
                Bpmn2Document.NS_BPMN2, "conditionExpression");
        assertNotNull(condExpr);
        assertEquals("ItemDefinition_1", condExpr.getAttribute("evaluatesToTypeRef"));
    }

    // ---- Update flow tests ----

    @Test
    void updateName() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
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
        args.addProperty("action", "update");
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

        // First, add a flow with a condition using FlowTool
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
        args.addProperty("action", "update");
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
        args.addProperty("action", "update");
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
        args.addProperty("action", "update");
        args.addProperty("id", "SequenceFlow_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("No properties to update"), ex.getMessage());
    }

    @Test
    void flowNotFoundOnUpdateThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "SequenceFlow_999");
        args.addProperty("name", "test");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Sequence flow not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("SequenceFlow_999"), ex.getMessage());
    }

    @Test
    void negativePriorityThrowsErrorOnUpdate() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "SequenceFlow_1");
        args.addProperty("priority", "-1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("positive"), ex.getMessage());
    }

    @Test
    void invalidEvaluatesToTypeRefThrowsErrorOnUpdate() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "SequenceFlow_1");
        args.addProperty("evaluatesToTypeRef", "NonExistent_ItemDef");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("ItemDefinition not found"), ex.getMessage());
    }

    // ---- Remove flow tests ----

    @Test
    void removeFlow() throws Exception {
        Path file = copyTestResource();
        // SequenceFlow_1 connects StartEvent_1 -> Task_1

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
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
        args.addProperty("action", "remove");
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

        // Add an extra flow from StartEvent_1 -> Task_2 so removing
        // SequenceFlow_1 doesn't leave StartEvent_1 without outgoing flows
        AddServiceTaskTool addServiceTaskTool = new AddServiceTaskTool();
        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("file", file.toString());
        taskArgs.addProperty("name", "Extra Task");
        taskArgs.addProperty("taskName", "com.example.IService_extra");
        taskArgs.addProperty("id", "Task_2");
        addServiceTaskTool.execute(new Args(taskArgs));

        JsonObject addFlowArgs = new JsonObject();
        addFlowArgs.addProperty("file", file.toString());
        addFlowArgs.addProperty("action", "add");
        addFlowArgs.addProperty("source", "StartEvent_1");
        addFlowArgs.addProperty("target", "Task_2");
        tool.execute(new Args(addFlowArgs));

        // Now remove SequenceFlow_1 (StartEvent_1 -> Task_1)
        // StartEvent_1 still has the new flow (outgoing), so no warning for it
        // Task_1 loses its only incoming flow, so warning expected
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
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
    void flowNotFoundOnRemoveThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "SequenceFlow_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Sequence flow not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("SequenceFlow_999"), ex.getMessage());
    }

    // ---- Helpers ----

    private void addExtraNodesAndFlowWithCondition(Path file) throws Exception {
        AddServiceTaskTool addServiceTaskTool = new AddServiceTaskTool();

        JsonObject taskArgs = new JsonObject();
        taskArgs.addProperty("file", file.toString());
        taskArgs.addProperty("name", "Extra Task");
        taskArgs.addProperty("taskName", "com.example.IService_extra");
        taskArgs.addProperty("id", "Task_2");
        addServiceTaskTool.execute(new Args(taskArgs));

        JsonObject flowArgs = new JsonObject();
        flowArgs.addProperty("file", file.toString());
        flowArgs.addProperty("action", "add");
        flowArgs.addProperty("source", "Task_1");
        flowArgs.addProperty("target", "Task_2");
        flowArgs.addProperty("condition", "return x > 0;");
        tool.execute(new Args(flowArgs));
    }

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
