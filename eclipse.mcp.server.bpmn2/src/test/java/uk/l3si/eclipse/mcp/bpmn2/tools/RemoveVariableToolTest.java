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

class RemoveVariableToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private RemoveVariableTool tool;

    @BeforeEach
    void setUp() {
        tool = new RemoveVariableTool();
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
    void nameIsRemoveVariable() {
        assertEquals("bpmn2_remove_variable", tool.getName());
    }

    @Test
    void removeVariable() throws Exception {
        Path file = copyTestResource();
        // "myVar" exists in the test file with ItemDefinition_1

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "myVar");

        JsonObject result = executeAndSerialize(args);

        assertEquals("myVar", result.get("name").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Re-parse and confirm variable is gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element property = findPropertyByName(doc, "myVar");
        assertNull(property, "Property should be removed");
    }

    @Test
    void removeVariableRemovesOrphanedItemDefinition() throws Exception {
        Path file = copyTestResource();
        // "myVar" is the only variable using ItemDefinition_1 (java.lang.String)

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "myVar");

        executeAndSerialize(args);

        // Re-parse and verify itemDefinition_1 is removed
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element itemDef = findItemDefinitionById(doc, "ItemDefinition_1");
        assertNull(itemDef, "Orphaned ItemDefinition should be removed");
    }

    @Test
    void removeVariableKeepsSharedItemDefinition() throws Exception {
        Path file = copyTestResource();

        // Add a second variable with same type as myVar (java.lang.String / ItemDefinition_1)
        AddVariableTool addTool = new AddVariableTool();
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("name", "otherStringVar");
        addArgs.addProperty("type", "java.lang.String");
        addTool.execute(new Args(addArgs));

        // Now remove myVar
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("name", "myVar");

        executeAndSerialize(removeArgs);

        // Re-parse — ItemDefinition_1 should still exist because otherStringVar uses it
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element itemDef = findItemDefinitionById(doc, "ItemDefinition_1");
        assertNotNull(itemDef, "Shared ItemDefinition should be kept");

        // But myVar property should be gone
        Element myVarProp = findPropertyByName(doc, "myVar");
        assertNull(myVarProp, "myVar property should be removed");

        // otherStringVar should still be there
        Element otherProp = findPropertyByName(doc, "otherStringVar");
        assertNotNull(otherProp, "otherStringVar property should still exist");
    }

    @Test
    void variableNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "nonExistent");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Variable not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("nonExistent"), ex.getMessage());
    }

    // ---- Helpers ----

    private static Element findPropertyByName(Bpmn2Document doc, String name) {
        for (Element prop : doc.listVariables()) {
            if (name.equals(prop.getAttribute("name"))) {
                return prop;
            }
        }
        return null;
    }

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
