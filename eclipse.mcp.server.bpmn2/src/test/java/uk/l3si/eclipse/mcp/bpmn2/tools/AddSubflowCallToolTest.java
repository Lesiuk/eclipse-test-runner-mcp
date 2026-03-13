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

class AddSubflowCallToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddSubflowCallTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddSubflowCallTool();
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
    void nameIsAddSubflowCall() {
        assertEquals("bpmn2_add_subflow_call", tool.getName());
    }

    @Test
    void addSubflowCall() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Sub Process");
        args.addProperty("calledElement", "com.example.sub_flow");

        JsonObject result = executeAndSerialize(args);

        assertEquals("callActivity", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("com.example.sub_flow", node.getAttribute("calledElement"));
        assertEquals("true", node.getAttributeNS(Bpmn2Document.NS_TNS, "waitForCompletion"));
        assertEquals("true", node.getAttributeNS(Bpmn2Document.NS_TNS, "independent"));

        Element ioSpec = findChildElement(node, Bpmn2Document.NS_BPMN2, "ioSpecification");
        assertNotNull(ioSpec, "ioSpecification should exist");
        Element dataInput = findChildElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataInput");
        assertNotNull(dataInput);
        assertEquals("processCommandFlow", dataInput.getAttribute("name"));
        assertEquals("ItemDefinition_1", dataInput.getAttribute("itemSubjectRef"));
    }

    @Test
    void missingCalledElementThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Missing CalledElement");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void duplicateIdThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Dup");
        args.addProperty("calledElement", "com.example.sub_flow");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("ID already taken"), ex.getMessage());
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
