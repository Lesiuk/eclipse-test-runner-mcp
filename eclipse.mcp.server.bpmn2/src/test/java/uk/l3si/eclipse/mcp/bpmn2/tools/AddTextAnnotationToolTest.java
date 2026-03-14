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

class AddTextAnnotationToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddTextAnnotationTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddTextAnnotationTool();
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
    void addAnnotationWithoutAttachment() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("text", "This is a comment");

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();

        assertNotNull(result.get("id"));
        assertFalse(result.has("associationId"));
    }

    @Test
    void addAnnotationAttachedToNode() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("text", "Handles the main request");
        args.addProperty("attachTo", "Task_1");

        JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();

        assertNotNull(result.get("id"));
        assertNotNull(result.get("associationId"));
        assertFalse(result.get("associationId").isJsonNull());
    }

    @Test
    void attachToNonExistentNodeThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("text", "Some note");
        args.addProperty("attachTo", "NonExistent_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Target element not found"), ex.getMessage());
    }
}
