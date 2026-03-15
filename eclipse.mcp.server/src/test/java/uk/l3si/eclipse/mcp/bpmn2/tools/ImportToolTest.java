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

class ImportToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private ImportTool tool;

    @BeforeEach
    void setUp() {
        tool = new ImportTool();
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
    void nameIsImport() {
        assertEquals("bpmn2_import", tool.getName());
    }

    // ---- Add tests ----

    @Test
    void addImport() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "com.example.MyUtils");

        JsonObject result = executeAndSerialize(args);

        assertEquals("com.example.MyUtils", result.get("name").getAsString());
    }

    @Test
    void duplicateImportThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "com.example.MyUtils");
        tool.execute(new Args(args));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Import already exists"), ex.getMessage());
    }

    // ---- Remove tests ----

    @Test
    void removeImport() throws Exception {
        Path file = copyTestResource();

        // First add an import
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("action", "add");
        addArgs.addProperty("name", "com.example.MyUtils");
        tool.execute(new Args(addArgs));

        // Now remove it
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
        removeArgs.addProperty("name", "com.example.MyUtils");

        JsonObject result = executeAndSerialize(removeArgs);

        assertEquals("com.example.MyUtils", result.get("name").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Verify the import is gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element process = doc.getProcessElement();
        Element extElements = findChildElement(process,
                Bpmn2Document.NS_BPMN2, "extensionElements");
        if (extElements != null) {
            NodeList children = extElements.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element el
                        && Bpmn2Document.NS_TNS.equals(el.getNamespaceURI())
                        && "import".equals(el.getLocalName())
                        && "com.example.MyUtils".equals(el.getAttribute("name"))) {
                    fail("Import should have been removed");
                }
            }
        }
    }

    @Test
    void importNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("name", "com.example.NonExistent");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Import not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("com.example.NonExistent"), ex.getMessage());
    }

    @Test
    void invalidAction() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("name", "com.example.MyUtils");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
    }

    // ---- Helpers ----

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
