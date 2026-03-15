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

class SignalToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private SignalTool tool;

    @BeforeEach
    void setUp() {
        tool = new SignalTool();
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
    void nameIsSignal() {
        assertEquals("bpmn2_signal", tool.getName());
    }

    // ---- Add tests ----

    @Test
    void addSignal() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
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
        args.addProperty("action", "add");
        args.addProperty("name", "com.example:anotherSignal");
        args.addProperty("id", "Task_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Task_1"), ex.getMessage());
        assertTrue(ex.getMessage().contains("already in use"), ex.getMessage());
    }

    // ---- Remove tests ----

    @Test
    void removeSignal() throws Exception {
        Path file = copyTestResource();

        // Signal_1 exists in the test file and is NOT referenced by any startEvent
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "Signal_1");

        JsonObject result = executeAndSerialize(args);

        assertEquals("Signal_1", result.get("id").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Verify the signal is gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        boolean found = doc.listSignals().stream()
                .anyMatch(s -> "Signal_1".equals(s.getAttribute("id")));
        assertFalse(found, "Signal should have been removed");
    }

    @Test
    void removeAddedSignal() throws Exception {
        Path file = copyTestResource();

        // Add a new signal, then remove it
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("action", "add");
        addArgs.addProperty("name", "com.example:testSignal");
        addArgs.addProperty("id", "Signal_Test");
        tool.execute(new Args(addArgs));

        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
        removeArgs.addProperty("id", "Signal_Test");

        JsonObject result = executeAndSerialize(removeArgs);

        assertEquals("Signal_Test", result.get("id").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Verify the signal is gone but Signal_1 is still there
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        boolean testSignalFound = doc.listSignals().stream()
                .anyMatch(s -> "Signal_Test".equals(s.getAttribute("id")));
        assertFalse(testSignalFound, "Signal_Test should have been removed");

        boolean signal1Found = doc.listSignals().stream()
                .anyMatch(s -> "Signal_1".equals(s.getAttribute("id")));
        assertTrue(signal1Found, "Signal_1 should still exist");
    }

    @Test
    void signalNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "NonExistent_Signal");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Signal not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NonExistent_Signal"), ex.getMessage());
    }

    @Test
    void removeReferencedSignalThrowsError() throws Exception {
        Path file = copyTestResource();

        // Add a signal and a start event that references it
        JsonObject addSignalArgs = new JsonObject();
        addSignalArgs.addProperty("file", file.toString());
        addSignalArgs.addProperty("action", "add");
        addSignalArgs.addProperty("name", "com.example:referencedSignal");
        addSignalArgs.addProperty("id", "Signal_Ref");
        tool.execute(new Args(addSignalArgs));

        // Add a signal start event referencing this signal
        NodeTool nodeTool = new NodeTool();
        JsonObject addStartArgs = new JsonObject();
        addStartArgs.addProperty("file", file.toString());
        addStartArgs.addProperty("action", "add");
        addStartArgs.addProperty("type", "start_event");
        addStartArgs.addProperty("name", "SignalStart");
        addStartArgs.addProperty("id", "StartEvent_Signal");
        addStartArgs.addProperty("signalRef", "Signal_Ref");
        nodeTool.execute(new Args(addStartArgs));

        // Now try to remove the signal — should fail
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
        removeArgs.addProperty("id", "Signal_Ref");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(removeArgs)));
        assertTrue(ex.getMessage().contains("Cannot remove signal"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Signal_Ref"), ex.getMessage());
        assertTrue(ex.getMessage().contains("StartEvent_Signal"), ex.getMessage());
    }

    @Test
    void invalidAction() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("name", "com.example:test");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
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
