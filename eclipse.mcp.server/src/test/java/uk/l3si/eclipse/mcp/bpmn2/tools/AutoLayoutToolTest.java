package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AutoLayoutToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AutoLayoutTool tool;

    @BeforeEach
    void setUp() {
        tool = new AutoLayoutTool();
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
    void nameIsAutoLayout() {
        assertEquals("bpmn2_auto_layout", tool.getName());
    }

    @Test
    void layoutTestFlowBpmn2() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        JsonObject result = executeAndSerialize(args);

        assertEquals(3, result.get("nodesLaid").getAsInt());
        assertEquals(file.toString(), result.get("file").getAsString());

        // Verify the file was saved and can be re-parsed
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNotNull(doc.getDiagramPlane());
    }

    @Test
    void emptyProcessThrowsError() throws Exception {
        Path file = tempDir.resolve("empty.bpmn2");
        Bpmn2Document.create(file.toString(), "com.example.empty", "empty", "com.example");

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("no nodes"), ex.getMessage());
    }

    @Test
    void missingFileThrowsError() {
        JsonObject args = new JsonObject();
        args.addProperty("file", tempDir.resolve("nonexistent.bpmn2").toString());

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }
}
