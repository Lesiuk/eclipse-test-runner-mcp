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

class AddNodeToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddNodeTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddNodeTool();
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
    void nameIsAddNode() {
        assertEquals("bpmn2_add_node", tool.getName());
    }

    // ---- Task ----

    @Test
    void addTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "task");
        args.addProperty("name", "My Task");
        args.addProperty("taskName", "com.example.IService_doSomething");

        JsonObject result = executeAndSerialize(args);

        assertNotNull(result.get("id"));
        assertEquals("task", result.get("type").getAsString());
        assertEquals("My Task", result.get("name").getAsString());

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("task", node.getLocalName());
        assertEquals("My Task", node.getAttribute("name"));
        assertEquals("com.example.IService_doSomething",
                node.getAttributeNS(Bpmn2Document.NS_TNS, "taskName"));

        // Verify ioSpecification exists
        Element ioSpec = findChildElement(node, Bpmn2Document.NS_BPMN2, "ioSpecification");
        assertNotNull(ioSpec, "ioSpecification should exist");
    }

    // ---- ScriptTask ----

    @Test
    void addScriptTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "scriptTask");
        args.addProperty("name", "Run Script");
        args.addProperty("script", "System.out.println(\"hello\");");

        JsonObject result = executeAndSerialize(args);

        assertEquals("scriptTask", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("scriptTask", node.getLocalName());
        assertEquals("http://www.java.com/java", node.getAttribute("scriptFormat"));

        Element scriptEl = findChildElement(node, Bpmn2Document.NS_BPMN2, "script");
        assertNotNull(scriptEl, "script child element should exist");
        assertEquals("System.out.println(\"hello\");", scriptEl.getTextContent());
    }

    // ---- CallActivity ----

    @Test
    void addCallActivity() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "callActivity");
        args.addProperty("name", "Sub Process");
        args.addProperty("calledElement", "com.example.sub_flow");

        JsonObject result = executeAndSerialize(args);

        assertEquals("callActivity", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("com.example.sub_flow", node.getAttribute("calledElement"));
    }

    // ---- ExclusiveGateway ----

    @Test
    void addExclusiveGatewayDiverging() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "exclusiveGateway");
        args.addProperty("name", "Check Condition");
        args.addProperty("direction", "diverging");

        JsonObject result = executeAndSerialize(args);

        assertEquals("exclusiveGateway", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("Diverging", node.getAttribute("gatewayDirection"));
    }

    // ---- StartEvent ----

    @Test
    void addStartEventWithSignalRef() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "startEvent");
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
    }

    // ---- EndEvent ----

    @Test
    void addEndEvent() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "endEvent");
        args.addProperty("name", "End");

        JsonObject result = executeAndSerialize(args);

        assertEquals("endEvent", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("endEvent", node.getLocalName());
    }

    // ---- ID handling ----

    @Test
    void autoGeneratedIdMatchesPattern() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "task");
        args.addProperty("name", "Auto ID Task");
        args.addProperty("taskName", "com.example.IService_auto");

        JsonObject result = executeAndSerialize(args);

        String id = result.get("id").getAsString();
        assertTrue(id.matches("Task_\\d+"), "ID should match Task_N pattern, got: " + id);
    }

    @Test
    void customId() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "task");
        args.addProperty("name", "Custom ID Task");
        args.addProperty("taskName", "com.example.IService_custom");
        args.addProperty("id", "MyTask");

        JsonObject result = executeAndSerialize(args);

        assertEquals("MyTask", result.get("id").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNotNull(doc.findNodeById("MyTask"));
    }

    @Test
    void duplicateIdThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "task");
        args.addProperty("name", "Duplicate");
        args.addProperty("taskName", "com.example.IService_dup");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("ID already taken"), ex.getMessage());
    }

    // ---- Type-specific validation errors ----

    @Test
    void missingTaskNameForTaskThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "task");
        args.addProperty("name", "Missing TaskName");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("'taskName' is required"), ex.getMessage());
    }

    @Test
    void missingScriptForScriptTaskThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "scriptTask");
        args.addProperty("name", "Missing Script");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("'script' is required"), ex.getMessage());
    }

    @Test
    void missingCalledElementForCallActivityThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "callActivity");
        args.addProperty("name", "Missing CalledElement");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("'calledElement' is required"), ex.getMessage());
    }

    @Test
    void missingDirectionForExclusiveGatewayThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "exclusiveGateway");
        args.addProperty("name", "Missing Direction");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("'direction' is required"), ex.getMessage());
    }

    @Test
    void invalidDirectionThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "exclusiveGateway");
        args.addProperty("name", "Bad Direction");
        args.addProperty("direction", "invalid");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid direction"), ex.getMessage());
    }

    @Test
    void invalidTypeThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "subProcess");
        args.addProperty("name", "Bad Type");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid type"), ex.getMessage());
    }

    @Test
    void duplicatePlainStartEventThrowsError() throws Exception {
        // test-flow.bpmn2 already has StartEvent_1 (plain, no signal)
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "startEvent");
        args.addProperty("name", "Second Start");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("plain startEvent"), ex.getMessage());
    }

    @Test
    void invalidSignalRefThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("type", "startEvent");
        args.addProperty("name", "Bad Signal Start");
        args.addProperty("signalRef", "Signal_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Signal not found"), ex.getMessage());
    }

    // ---- Helper ----

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
