package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AddItemDefinitionToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddItemDefinitionTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddItemDefinitionTool();
    }

    private Path copyTestResource() throws Exception {
        Path target = tempDir.resolve("test-flow.bpmn2");
        try (InputStream in = getClass().getResourceAsStream("/test-flow.bpmn2")) {
            assertNotNull(in, "Test resource not found");
            Files.copy(in, target);
        }
        return target;
    }

    @Test
    void addItemDefinition() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("structureRef", "com.example.MyType");

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();

        assertNotNull(result.get("id"));
        assertEquals("com.example.MyType", result.get("structureRef").getAsString());
    }

    @Test
    void duplicateStructureRefThrowsError() throws Exception {
        Path file = copyTestResource();

        // java.lang.String already exists as ItemDefinition_1 in test-flow.bpmn2
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("structureRef", "java.lang.String");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
    }

    @Test
    void customIdWorks() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("structureRef", "com.example.CustomType");
        args.addProperty("id", "MyCustomItemDef");

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();

        assertEquals("MyCustomItemDef", result.get("id").getAsString());
    }
}
