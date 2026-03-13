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

class UpdateNodeToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private UpdateNodeTool tool;

    @BeforeEach
    void setUp() {
        tool = new UpdateNodeTool();
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
    void nameIsUpdateNode() {
        assertEquals("bpmn2_update_node", tool.getName());
    }

    // ---- Update name ----

    @Test
    void updateNameOfTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_1");
        args.addProperty("name", "Updated Task Name");

        JsonObject result = executeAndSerialize(args);

        assertEquals("Task_1", result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("name"));

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById("Task_1");
        assertNotNull(node);
        assertEquals("Updated Task Name", node.getAttribute("name"));
    }

    // ---- Update script ----

    @Test
    void updateScriptOfScriptTask() throws Exception {
        Path file = copyTestResource();

        // First add a scriptTask
        AddNodeTool addTool = new AddNodeTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("type", "scriptTask");
        addArgs.addProperty("name", "My Script");
        addArgs.addProperty("script", "int x = 1;");
        Object addResult = addTool.execute(new Args(addArgs));
        String scriptTaskId = GSON.toJsonTree(addResult).getAsJsonObject()
                .get("id").getAsString();

        // Now update the script
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", scriptTaskId);
        args.addProperty("script", "int x = 42;");

        JsonObject result = executeAndSerialize(args);

        assertEquals(scriptTaskId, result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("script"));

        // Re-parse and verify
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

        // First add a callActivity
        AddNodeTool addTool = new AddNodeTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("type", "callActivity");
        addArgs.addProperty("name", "Sub Process");
        addArgs.addProperty("calledElement", "com.example.old_flow");
        Object addResult = addTool.execute(new Args(addArgs));
        String callActivityId = GSON.toJsonTree(addResult).getAsJsonObject()
                .get("id").getAsString();

        // Now update the calledElement
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", callActivityId);
        args.addProperty("calledElement", "com.example.new_flow");

        JsonObject result = executeAndSerialize(args);

        assertEquals(callActivityId, result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("calledElement"));

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(callActivityId);
        assertNotNull(node);
        assertEquals("com.example.new_flow", node.getAttribute("calledElement"));
    }

    // ---- Update direction ----

    @Test
    void updateDirectionOfGateway() throws Exception {
        Path file = copyTestResource();

        // First add an exclusiveGateway
        AddNodeTool addTool = new AddNodeTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("type", "exclusiveGateway");
        addArgs.addProperty("name", "Check");
        addArgs.addProperty("direction", "diverging");
        Object addResult = addTool.execute(new Args(addArgs));
        String gatewayId = GSON.toJsonTree(addResult).getAsJsonObject()
                .get("id").getAsString();

        // Now update the direction
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", gatewayId);
        args.addProperty("direction", "converging");

        JsonObject result = executeAndSerialize(args);

        assertEquals(gatewayId, result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("direction"));

        // Re-parse and verify
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
        args.addProperty("id", "Task_1");
        args.addProperty("taskName", "com.example.IService_newMethod");

        JsonObject result = executeAndSerialize(args);

        assertEquals("Task_1", result.get("id").getAsString());
        assertTrue(result.get("updated").getAsJsonArray().toString().contains("taskName"));

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById("Task_1");
        assertNotNull(node);
        assertEquals("com.example.IService_newMethod",
                node.getAttributeNS(Bpmn2Document.NS_TNS, "taskName"));
    }

    // ---- Error: script on a task ----

    @Test
    void setScriptOnTaskThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_1");
        args.addProperty("script", "int x = 1;");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Cannot set 'script' on a task"),
                ex.getMessage());
        assertTrue(ex.getMessage().contains("scriptTask"), ex.getMessage());
    }

    // ---- Error: calledElement on a scriptTask ----

    @Test
    void setCalledElementOnScriptTaskThrowsError() throws Exception {
        Path file = copyTestResource();

        // First add a scriptTask
        AddNodeTool addTool = new AddNodeTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("type", "scriptTask");
        addArgs.addProperty("name", "My Script");
        addArgs.addProperty("script", "int x = 1;");
        Object addResult = addTool.execute(new Args(addArgs));
        String scriptTaskId = GSON.toJsonTree(addResult).getAsJsonObject()
                .get("id").getAsString();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", scriptTaskId);
        args.addProperty("calledElement", "com.example.flow");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Cannot set 'calledElement' on a scriptTask"),
                ex.getMessage());
        assertTrue(ex.getMessage().contains("callActivity"), ex.getMessage());
    }

    // ---- Error: no properties ----

    @Test
    void noPropertiesThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("No properties to update"), ex.getMessage());
    }

    // ---- Error: node not found ----

    @Test
    void nodeNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("id", "Task_999");
        args.addProperty("name", "Anything");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
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
