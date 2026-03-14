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

class AddImportToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddImportTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddImportTool();
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
    void addImport() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example.MyUtils");

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();

        assertEquals("com.example.MyUtils", result.get("name").getAsString());
    }

    @Test
    void duplicateImportThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example.MyUtils");
        tool.execute(new Args(args));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Import already exists"), ex.getMessage());
    }
}
