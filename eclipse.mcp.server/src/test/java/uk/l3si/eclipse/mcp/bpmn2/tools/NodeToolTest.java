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

class NodeToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private NodeTool tool;

    @BeforeEach
    void setUp() {
        tool = new NodeTool();
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
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    private String addScriptTask(Path file, String name, String script) throws Exception {
        AddScriptTaskTool addTool = new AddScriptTaskTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("name", name);
        addArgs.addProperty("script", script);
        Object addResult = addTool.execute(new Args(addArgs), message -> {});
        return GSON.toJsonTree(addResult).getAsJsonObject().get("id").getAsString();
    }

    private String addSubflowCall(Path file, String name, String calledElement) throws Exception {
        AddSubflowCallTool addTool = new AddSubflowCallTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("name", name);
        addArgs.addProperty("calledElement", calledElement);
        Object addResult = addTool.execute(new Args(addArgs), message -> {});
        return GSON.toJsonTree(addResult).getAsJsonObject().get("id").getAsString();
    }

    private String addGateway(Path file, String name, String direction) throws Exception {
        AddGatewayTool addTool = new AddGatewayTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("name", name);
        addArgs.addProperty("direction", direction);
        Object addResult = addTool.execute(new Args(addArgs), message -> {});
        return GSON.toJsonTree(addResult).getAsJsonObject().get("id").getAsString();
    }

    // ---- General ----

    @Test
    void nameIsNode() {
        assertEquals("bpmn2_node", tool.getName());
    }

    @Test
    void invalidActionThrowsError() {
        JsonObject args = new JsonObject();
        args.addProperty("file", "/dummy.bpmn2");
        args.addProperty("action", "invalid");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
        assertTrue(ex.getMessage().contains("invalid"), ex.getMessage());
    }

    // ---- Add start event tests ----

    @Test
    void addStartEventWithSignalRef() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "start_event");
        args.addProperty("name", "Signal Start");
        args.addProperty("signalRef", "Signal_1");

        JsonObject result = executeAndSerialize(args);

        assertEquals("startEvent", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);

        Element signalEventDef = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "signalEventDefinition");
        assertNotNull(signalEventDef, "signalEventDefinition child should exist");
        assertEquals("Signal_1", signalEventDef.getAttribute("signalRef"));

        Element dataOutput = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "dataOutput");
        assertNotNull(dataOutput, "signal start event should have dataOutput");

        Element dataOutputAssoc = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        assertNotNull(dataOutputAssoc, "signal start event should have dataOutputAssociation");

        Element outputSet = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "outputSet");
        assertNotNull(outputSet, "signal start event should have outputSet");
    }

    @Test
    void duplicatePlainStartEventThrowsError() throws Exception {
        // test-flow.bpmn2 already has StartEvent_1 (plain, no signal)
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "start_event");
        args.addProperty("name", "Second Start");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("plain startEvent"), ex.getMessage());
    }

    @Test
    void invalidSignalRefThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "start_event");
        args.addProperty("name", "Bad Signal Start");
        args.addProperty("signalRef", "Signal_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Signal not found"), ex.getMessage());
    }

    // ---- Add end event tests ----

    @Test
    void addEndEvent() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "end_event");
        args.addProperty("name", "End");

        JsonObject result = executeAndSerialize(args);

        assertEquals("endEvent", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("endEvent", node.getLocalName());
    }

    @Test
    void addEndEventWithCustomId() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "end_event");
        args.addProperty("name", "Custom End");
        args.addProperty("id", "MyEnd");

        JsonObject result = executeAndSerialize(args);

        assertEquals("MyEnd", result.get("id").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNotNull(doc.findNodeById("MyEnd"));
    }

    @Test
    void addEndEventDuplicateIdThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "end_event");
        args.addProperty("name", "End");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("ID already taken"), ex.getMessage());
    }

    // ---- Add extension point tests ----

    @Test
    void addExtensionPoint() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "extension_point");
        args.addProperty("name", "Web Review");
        args.addProperty("groupId", "dynamo.review");

        JsonObject result = executeAndSerialize(args);

        assertEquals("userTask", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("userTask", node.getLocalName());

        // Verify ioSpecification with 11 dataInputs
        Element ioSpec = findChildElement(node, Bpmn2Document.NS_BPMN2, "ioSpecification");
        assertNotNull(ioSpec, "ioSpecification should exist");

        int dataInputCount = 0;
        NodeList ioChildren = ioSpec.getChildNodes();
        for (int i = 0; i < ioChildren.getLength(); i++) {
            if (ioChildren.item(i) instanceof Element el
                    && "dataInput".equals(el.getLocalName())) {
                dataInputCount++;
            }
        }
        assertEquals(11, dataInputCount, "UserTask should have 11 dataInputs");

        // Verify dataOutputAssociation exists
        Element dataOutputAssoc = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        assertNotNull(dataOutputAssoc, "UserTask should have dataOutputAssociation");
    }

    @Test
    void addExtensionPointWithoutGroupId() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "extension_point");
        args.addProperty("name", "No Group Review");

        JsonObject result = executeAndSerialize(args);

        assertEquals("userTask", result.get("type").getAsString());
        assertNotNull(result.get("id"));
    }

    // ---- Add: invalid type ----

    @Test
    void addInvalidTypeThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "bogus");
        args.addProperty("name", "Test");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Invalid type"), ex.getMessage());
        assertTrue(ex.getMessage().contains("bogus"), ex.getMessage());
    }

    // ---- Update name ----

    @Test
    void updateNameOfTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_1");
        args.addProperty("name", "Updated Task Name");

        JsonObject result = executeAndSerialize(args);

        assertEquals("Task_1", result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("name"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById("Task_1");
        assertNotNull(node);
        assertEquals("Updated Task Name", node.getAttribute("name"));
    }

    // ---- Update script ----

    @Test
    void updateScriptOfScriptTask() throws Exception {
        Path file = copyTestResource();
        String scriptTaskId = addScriptTask(file, "My Script", "int x = 1;");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", scriptTaskId);
        args.addProperty("script", "int x = 42;");

        JsonObject result = executeAndSerialize(args);

        assertEquals(scriptTaskId, result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("script"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(scriptTaskId);
        assertNotNull(node);
        Element scriptEl = findChildElement(node, Bpmn2Document.NS_BPMN2, "script");
        assertNotNull(scriptEl);
        assertEquals("int x = 42;", scriptEl.getTextContent());
    }

    // ---- Update calledElement ----

    @Test
    void updateCalledElementOfCallActivity() throws Exception {
        Path file = copyTestResource();
        String callActivityId = addSubflowCall(file, "Sub Process", "com.example.old_flow");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", callActivityId);
        args.addProperty("calledElement", "com.example.new_flow");

        JsonObject result = executeAndSerialize(args);

        assertEquals(callActivityId, result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("calledElement"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(callActivityId);
        assertNotNull(node);
        assertEquals("com.example.new_flow", node.getAttribute("calledElement"));
    }

    // ---- Update direction ----

    @Test
    void updateDirectionOfGateway() throws Exception {
        Path file = copyTestResource();
        String gatewayId = addGateway(file, "Check", "diverging");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", gatewayId);
        args.addProperty("direction", "converging");

        JsonObject result = executeAndSerialize(args);

        assertEquals(gatewayId, result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("direction"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(gatewayId);
        assertNotNull(node);
        assertEquals("Converging", node.getAttribute("gatewayDirection"));
    }

    // ---- Update taskName ----

    @Test
    void updateTaskNameOfTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_1");
        args.addProperty("taskName", "com.example.IService_newMethod");

        JsonObject result = executeAndSerialize(args);

        assertEquals("Task_1", result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("taskName"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById("Task_1");
        assertNotNull(node);
        assertEquals("com.example.IService_newMethod",
                node.getAttributeNS(Bpmn2Document.NS_TNS, "taskName"));
    }

    // ---- Update displayName ----

    @Test
    void updateDisplayNameOfTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_1");
        args.addProperty("displayName", "New Display Name");

        JsonObject result = executeAndSerialize(args);

        assertTrue(result.get("updated").getAsJsonArray().toString().contains("displayName"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById("Task_1");
        assertNotNull(node);
        assertEquals("New Display Name",
                node.getAttributeNS(Bpmn2Document.NS_TNS, "displayName"));
    }

    // ---- Update icon ----

    @Test
    void updateIconOfTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_1");
        args.addProperty("icon", "new-icon.gif");

        JsonObject result = executeAndSerialize(args);

        assertTrue(result.get("updated").getAsJsonArray().toString().contains("icon"));

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById("Task_1");
        assertNotNull(node);
        assertEquals("new-icon.gif",
                node.getAttributeNS(Bpmn2Document.NS_TNS, "icon"));
    }

    // ---- Error: script on a task ----

    @Test
    void setScriptOnTaskThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_1");
        args.addProperty("script", "int x = 1;");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Cannot set 'script' on a task"),
                ex.getMessage());
        assertTrue(ex.getMessage().contains("scriptTask"), ex.getMessage());
    }

    // ---- Error: calledElement on a scriptTask ----

    @Test
    void setCalledElementOnScriptTaskThrowsError() throws Exception {
        Path file = copyTestResource();
        String scriptTaskId = addScriptTask(file, "My Script", "int x = 1;");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", scriptTaskId);
        args.addProperty("calledElement", "com.example.flow");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Cannot set 'calledElement' on a scriptTask"),
                ex.getMessage());
        assertTrue(ex.getMessage().contains("callActivity"), ex.getMessage());
    }

    // ---- Error: icon on a scriptTask ----

    @Test
    void setIconOnScriptTaskThrowsError() throws Exception {
        Path file = copyTestResource();
        String scriptTaskId = addScriptTask(file, "My Script", "int x = 1;");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", scriptTaskId);
        args.addProperty("icon", "icon.gif");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Cannot set 'icon' on a scriptTask"),
                ex.getMessage());
    }

    // ---- Error: no properties ----

    @Test
    void noPropertiesThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("No properties to update"), ex.getMessage());
    }

    // ---- Error: node not found (update) ----

    @Test
    void nodeNotFoundOnUpdateThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("id", "Task_999");
        args.addProperty("name", "Anything");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
    }

    // ---- Remove node tests ----

    @Test
    void removeTaskRemovesNodeAndConnectedFlows() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
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
        args.addProperty("action", "remove");
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
        args.addProperty("action", "remove");
        args.addProperty("id", "StartEvent_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Cannot remove the only startEvent"),
                ex.getMessage());
    }

    // ---- Error: node not found (remove) ----

    @Test
    void nodeNotFoundOnRemoveThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "Task_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
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
