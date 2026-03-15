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

class CreateProcessToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private CreateProcessTool tool;

    @BeforeEach
    void setUp() {
        tool = new CreateProcessTool();
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsCreateProcess() {
        assertEquals("bpmn2_create_process", tool.getName());
    }

    @Test
    void createNewProcess() throws Exception {
        Path file = tempDir.resolve("new-flow.bpmn2");
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "com.example.new_flow");
        args.addProperty("processName", "new_flow");
        args.addProperty("packageName", "com.example");

        JsonObject result = executeAndSerialize(args);

        assertEquals(file.toString(), result.get("file").getAsString());
        assertEquals("com.example.new_flow", result.get("processId").getAsString());
        assertTrue(Files.exists(file));

        // Parse it back with GetProcessTool and verify
        GetProcessTool getProcessTool = new GetProcessTool();
        JsonObject getArgs = new JsonObject();
        getArgs.addProperty("file", file.toString());
        JsonObject processInfo = GSON.toJsonTree(
                getProcessTool.execute(new Args(getArgs))).getAsJsonObject();

        assertEquals("com.example.new_flow", processInfo.get("processId").getAsString());
        assertEquals("new_flow", processInfo.get("processName").getAsString());
        assertEquals("com.example", processInfo.get("packageName").getAsString());
        assertEquals(0, processInfo.getAsJsonArray("nodes").size());
        assertEquals(0, processInfo.getAsJsonArray("flows").size());
        assertEquals(0, processInfo.getAsJsonArray("variables").size());
        assertEquals(0, processInfo.getAsJsonArray("signals").size());
    }

    @Test
    void fileAlreadyExistsThrowsError() throws Exception {
        Path file = tempDir.resolve("existing.bpmn2");
        try (InputStream in = getClass().getResourceAsStream("/test-flow.bpmn2")) {
            assertNotNull(in, "Test resource not found");
            Files.copy(in, file);
        }

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "com.example.flow");
        args.addProperty("processName", "flow");
        args.addProperty("packageName", "com.example");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("File already exists"), ex.getMessage());
    }

    @Test
    void invalidProcessIdThrowsError() {
        Path file = tempDir.resolve("bad-id.bpmn2");
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "invalid id with spaces!");
        args.addProperty("processName", "test");
        args.addProperty("packageName", "com.example");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Invalid processId"), ex.getMessage());
    }

    @Test
    void missingFileParameterThrowsError() {
        JsonObject args = new JsonObject();
        args.addProperty("processId", "com.example.flow");
        args.addProperty("processName", "flow");
        args.addProperty("packageName", "com.example");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void missingProcessIdThrowsError() {
        Path file = tempDir.resolve("no-id.bpmn2");
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processName", "flow");
        args.addProperty("packageName", "com.example");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void missingProcessNameThrowsError() {
        Path file = tempDir.resolve("no-name.bpmn2");
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "com.example.flow");
        args.addProperty("packageName", "com.example");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }

    @Test
    void wrongFileExtensionThrowsError() {
        Path file = tempDir.resolve("bad-extension.xml");
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "com.example.flow");
        args.addProperty("processName", "flow");
        args.addProperty("packageName", "com.example");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains(".bpmn2"), ex.getMessage());
    }

    @Test
    void missingPackageNameThrowsError() {
        Path file = tempDir.resolve("no-pkg.bpmn2");
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "com.example.flow");
        args.addProperty("processName", "flow");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
    }
}
