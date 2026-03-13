package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AddEndEventToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddEndEventTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddEndEventTool();
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
    void nameIsAddEndEvent() {
        assertEquals("bpmn2_add_end_event", tool.getName());
    }

    @Test
    void addEndEvent() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "End");

        JsonObject result = executeAndSerialize(args);

        assertEquals("endEvent", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("endEvent", node.getLocalName());
    }

    @Test
    void addEndEventWithCustomId() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Custom End");
        args.addProperty("id", "MyEnd");

        JsonObject result = executeAndSerialize(args);

        assertEquals("MyEnd", result.get("id").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNotNull(doc.findNodeById("MyEnd"));
    }

    @Test
    void duplicateIdThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "End");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("ID already taken"), ex.getMessage());
    }
}
