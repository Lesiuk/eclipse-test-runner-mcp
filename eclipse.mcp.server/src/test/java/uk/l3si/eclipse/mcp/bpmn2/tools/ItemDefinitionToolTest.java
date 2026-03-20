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

class ItemDefinitionToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private ItemDefinitionTool tool;

    @BeforeEach
    void setUp() {
        tool = new ItemDefinitionTool();
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

    @Test
    void nameIsItemDefinition() {
        assertEquals("bpmn2_item_definition", tool.getName());
    }

    // ---- Add tests ----

    @Test
    void addItemDefinition() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("structureRef", "com.example.MyType");

        JsonObject result = executeAndSerialize(args);

        assertNotNull(result.get("id"));
        assertEquals("com.example.MyType", result.get("structureRef").getAsString());
    }

    @Test
    void duplicateStructureRefThrowsError() throws Exception {
        Path file = copyTestResource();

        // java.lang.String already exists as ItemDefinition_1 in test-flow.bpmn2
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("structureRef", "java.lang.String");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
    }

    @Test
    void customIdWorks() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("structureRef", "com.example.CustomType");
        args.addProperty("id", "MyCustomItemDef");

        JsonObject result = executeAndSerialize(args);

        assertEquals("MyCustomItemDef", result.get("id").getAsString());
    }

    // ---- Remove tests ----

    @Test
    void removeItemDefinition() throws Exception {
        Path file = copyTestResource();

        // Add a new itemDefinition, then remove it
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("action", "add");
        addArgs.addProperty("structureRef", "com.example.CustomType");
        addArgs.addProperty("id", "ItemDefinition_Custom");
        tool.execute(new Args(addArgs), message -> {});

        // Remove it
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
        removeArgs.addProperty("id", "ItemDefinition_Custom");

        JsonObject result = executeAndSerialize(removeArgs);

        assertEquals("ItemDefinition_Custom", result.get("id").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Verify it's gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNull(findItemDefinitionById(doc, "ItemDefinition_Custom"),
                "ItemDefinition should have been removed");
    }

    @Test
    void itemDefinitionNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "NonExistent_ItemDef");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("ItemDefinition not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NonExistent_ItemDef"), ex.getMessage());
    }

    @Test
    void removeReferencedItemDefinitionThrowsError() throws Exception {
        Path file = copyTestResource();

        // ItemDefinition_1 is referenced by variable myVar in the test file
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "ItemDefinition_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Cannot remove itemDefinition"), ex.getMessage());
        assertTrue(ex.getMessage().contains("ItemDefinition_1"), ex.getMessage());
        assertTrue(ex.getMessage().contains("myVar"), ex.getMessage());
    }

    @Test
    void invalidAction() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("structureRef", "com.example.MyType");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
    }

    // ---- Helpers ----

    private static Element findItemDefinitionById(Bpmn2Document doc, String id) {
        Element definitions = doc.getDefinitionsElement();
        NodeList children = definitions.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "itemDefinition".equals(el.getLocalName())
                    && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }
}
