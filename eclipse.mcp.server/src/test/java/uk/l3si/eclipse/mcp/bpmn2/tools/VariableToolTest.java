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

class VariableToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private VariableTool tool;

    @BeforeEach
    void setUp() {
        tool = new VariableTool();
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
    void nameIsVariable() {
        assertEquals("bpmn2_variable", tool.getName());
    }

    // ---- Add tests ----

    @Test
    void addVariable() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "orderCount");
        args.addProperty("type", "java.lang.Integer");

        JsonObject result = executeAndSerialize(args);

        assertEquals("orderCount", result.get("name").getAsString());
        assertEquals("java.lang.Integer", result.get("type").getAsString());
        assertNotNull(result.get("itemDefinitionId"));

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        // Verify property exists
        Element property = findPropertyByName(doc, "orderCount");
        assertNotNull(property, "Property should exist after adding");
        assertEquals("orderCount", property.getAttribute("id"));
        assertEquals("orderCount", property.getAttribute("name"));

        // Verify itemDefinition exists
        String itemDefId = property.getAttribute("itemSubjectRef");
        Element itemDef = findItemDefinitionById(doc, itemDefId);
        assertNotNull(itemDef, "ItemDefinition should exist");
        assertEquals("java.lang.Integer", itemDef.getAttribute("structureRef"));
    }

    @Test
    void reuseExistingItemDefinition() throws Exception {
        Path file = copyTestResource();

        // Add first variable with type java.lang.String (ItemDefinition_1 already exists for this)
        JsonObject args1 = new JsonObject();
        args1.addProperty("file", file.toString());
        args1.addProperty("action", "add");
        args1.addProperty("name", "firstName");
        args1.addProperty("type", "java.lang.String");

        JsonObject result1 = executeAndSerialize(args1);
        String itemDefId1 = result1.get("itemDefinitionId").getAsString();

        // Add second variable with same type
        JsonObject args2 = new JsonObject();
        args2.addProperty("file", file.toString());
        args2.addProperty("action", "add");
        args2.addProperty("name", "lastName");
        args2.addProperty("type", "java.lang.String");

        JsonObject result2 = executeAndSerialize(args2);
        String itemDefId2 = result2.get("itemDefinitionId").getAsString();

        // Both should reference the same itemDefinition
        assertEquals(itemDefId1, itemDefId2, "Should reuse existing ItemDefinition");

        // Verify only one itemDefinition with structureRef=java.lang.String
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        int count = countItemDefinitionsWithStructureRef(doc, "java.lang.String");
        assertEquals(1, count, "Should have only one ItemDefinition for java.lang.String");
    }

    @Test
    void duplicateVariableNameThrowsError() throws Exception {
        Path file = copyTestResource();
        // "myVar" already exists in the test file

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "myVar");
        args.addProperty("type", "java.lang.Integer");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("myVar"), ex.getMessage());
        assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
    }

    @Test
    void invalidVariableNameStartsWithDigit() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "1invalid");
        args.addProperty("type", "java.lang.String");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid variable name"), ex.getMessage());
        assertTrue(ex.getMessage().contains("1invalid"), ex.getMessage());
    }

    @Test
    void invalidVariableNameContainsSpaces() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "my var");
        args.addProperty("type", "java.lang.String");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid variable name"), ex.getMessage());
    }

    @Test
    void missingFileParameter() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "add");
        args.addProperty("name", "myVar");
        args.addProperty("type", "java.lang.String");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void missingNameParameter() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("type", "java.lang.String");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void missingTypeParameterForAdd() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("name", "newVar");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void invalidAction() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("name", "myVar");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
    }

    // ---- Remove tests ----

    @Test
    void removeVariable() throws Exception {
        Path file = copyTestResource();
        // "myVar" exists in the test file with ItemDefinition_1

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
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
        args.addProperty("action", "remove");
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
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("action", "add");
        addArgs.addProperty("name", "otherStringVar");
        addArgs.addProperty("type", "java.lang.String");
        tool.execute(new Args(addArgs));

        // Now remove myVar
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
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
        args.addProperty("action", "remove");
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

    private static int countItemDefinitionsWithStructureRef(Bpmn2Document doc, String structureRef) {
        int count = 0;
        Element definitions = doc.getDefinitionsElement();
        NodeList children = definitions.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "itemDefinition".equals(el.getLocalName())
                    && structureRef.equals(el.getAttribute("structureRef"))) {
                count++;
            }
        }
        return count;
    }
}
