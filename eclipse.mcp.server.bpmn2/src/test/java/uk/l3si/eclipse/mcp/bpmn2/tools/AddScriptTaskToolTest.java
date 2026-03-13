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

class AddScriptTaskToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddScriptTaskTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddScriptTaskTool();
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
    void nameIsAddScriptTask() {
        assertEquals("bpmn2_add_script_task", tool.getName());
    }

    @Test
    void addScriptTask() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
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

    @Test
    void addScriptTaskWithCustomFormat() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Custom Script");
        args.addProperty("script", "print('hello')");
        args.addProperty("scriptFormat", "http://www.python.org/python");

        JsonObject result = executeAndSerialize(args);

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertEquals("http://www.python.org/python", node.getAttribute("scriptFormat"));
    }

    @Test
    void missingScriptThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Missing Script");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
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
