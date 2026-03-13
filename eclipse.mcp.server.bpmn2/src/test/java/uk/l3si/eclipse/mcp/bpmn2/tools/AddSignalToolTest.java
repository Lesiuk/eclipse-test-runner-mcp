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

class AddSignalToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddSignalTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddSignalTool();
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
    void nameIsAddSignal() {
        assertEquals("bpmn2_add_signal", tool.getName());
    }

    @Test
    void addSignal() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example:afterOutput");

        JsonObject result = executeAndSerialize(args);

        assertNotNull(result.get("id"));
        assertEquals("com.example:afterOutput", result.get("name").getAsString());

        // Re-parse and verify signal exists in definitions
        String signalId = result.get("id").getAsString();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element signal = findSignalById(doc, signalId);
        assertNotNull(signal, "Signal should exist after adding");
        assertEquals("com.example:afterOutput", signal.getAttribute("name"));
    }

    @Test
    void addSignalWithCustomId() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example:customSignal");
        args.addProperty("id", "MyCustomSignal_42");

        JsonObject result = executeAndSerialize(args);

        assertEquals("MyCustomSignal_42", result.get("id").getAsString());
        assertEquals("com.example:customSignal", result.get("name").getAsString());

        // Re-parse and verify
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element signal = findSignalById(doc, "MyCustomSignal_42");
        assertNotNull(signal, "Signal with custom ID should exist");
        assertEquals("com.example:customSignal", signal.getAttribute("name"));
    }

    @Test
    void duplicateSignalNameThrowsError() throws Exception {
        Path file = copyTestResource();
        // "com.example:beforeInput" already exists as Signal_1 in the test file

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example:beforeInput");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("com.example:beforeInput"), ex.getMessage());
        assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
    }

    @Test
    void duplicateIdThrowsError() throws Exception {
        Path file = copyTestResource();
        // "Signal_1" already exists as an id in the test file

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example:newSignal");
        args.addProperty("id", "Signal_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Signal_1"), ex.getMessage());
        assertTrue(ex.getMessage().contains("already in use"), ex.getMessage());
    }

    @Test
    void duplicateIdFromOtherElementThrowsError() throws Exception {
        Path file = copyTestResource();
        // "Task_1" is used by a task element

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "com.example:anotherSignal");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Task_1"), ex.getMessage());
        assertTrue(ex.getMessage().contains("already in use"), ex.getMessage());
    }

    // ---- Helpers ----

    private static Element findSignalById(Bpmn2Document doc, String id) {
        for (Element signal : doc.listSignals()) {
            if (id.equals(signal.getAttribute("id"))) {
                return signal;
            }
        }
        return null;
    }
}
