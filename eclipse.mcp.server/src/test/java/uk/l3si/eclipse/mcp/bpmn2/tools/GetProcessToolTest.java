package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GetProcessToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private GetProcessTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetProcessTool();
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
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    @Test
    void nameIsGetProcess() {
        assertEquals("bpmn2_get_process", tool.getName());
    }

    @Test
    void parseTestFlow() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        JsonObject result = executeAndSerialize(args);

        assertEquals("com.example.test_flow", result.get("processId").getAsString());
        assertEquals("test_flow", result.get("processName").getAsString());
        assertEquals("com.example", result.get("packageName").getAsString());

        JsonArray nodes = result.getAsJsonArray("nodes");
        assertEquals(3, nodes.size());

        JsonArray flows = result.getAsJsonArray("flows");
        assertEquals(2, flows.size());

        JsonArray variables = result.getAsJsonArray("variables");
        assertEquals(1, variables.size());

        JsonArray signals = result.getAsJsonArray("signals");
        assertEquals(1, signals.size());
    }

    @Test
    void nodeTypesAreCorrect() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        JsonObject result = executeAndSerialize(args);
        JsonArray nodes = result.getAsJsonArray("nodes");

        JsonObject startEvent = nodes.get(0).getAsJsonObject();
        assertEquals("StartEvent_1", startEvent.get("id").getAsString());
        assertEquals("startEvent", startEvent.get("type").getAsString());
        assertEquals("StartProcess", startEvent.get("name").getAsString());

        JsonObject task = nodes.get(1).getAsJsonObject();
        assertEquals("Task_1", task.get("id").getAsString());
        assertEquals("task", task.get("type").getAsString());
        assertEquals("Handle Request", task.get("name").getAsString());
        assertEquals("com.example.IService_handle", task.get("taskName").getAsString());

        JsonObject endEvent = nodes.get(2).getAsJsonObject();
        assertEquals("EndEvent_1", endEvent.get("id").getAsString());
        assertEquals("endEvent", endEvent.get("type").getAsString());
        assertEquals("EndProcess", endEvent.get("name").getAsString());
    }

    @Test
    void flowSourceAndTargetAreCorrect() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        JsonObject result = executeAndSerialize(args);
        JsonArray flows = result.getAsJsonArray("flows");

        JsonObject flow1 = flows.get(0).getAsJsonObject();
        assertEquals("SequenceFlow_1", flow1.get("id").getAsString());
        assertEquals("StartEvent_1", flow1.get("source").getAsString());
        assertEquals("Task_1", flow1.get("target").getAsString());

        JsonObject flow2 = flows.get(1).getAsJsonObject();
        assertEquals("SequenceFlow_2", flow2.get("id").getAsString());
        assertEquals("Task_1", flow2.get("source").getAsString());
        assertEquals("EndEvent_1", flow2.get("target").getAsString());
    }

    @Test
    void variableInfoIsCorrect() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        JsonObject result = executeAndSerialize(args);
        JsonArray variables = result.getAsJsonArray("variables");

        JsonObject var = variables.get(0).getAsJsonObject();
        assertEquals("myVar", var.get("name").getAsString());
        assertEquals("java.lang.String", var.get("type").getAsString());
    }

    @Test
    void signalInfoIsCorrect() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());

        JsonObject result = executeAndSerialize(args);
        JsonArray signals = result.getAsJsonArray("signals");

        JsonObject signal = signals.get(0).getAsJsonObject();
        assertEquals("Signal_1", signal.get("id").getAsString());
        assertEquals("com.example:beforeInput", signal.get("name").getAsString());
    }

    @Test
    void fileNotFoundThrowsError() {
        JsonObject args = new JsonObject();
        args.addProperty("file", tempDir.resolve("nonexistent.bpmn2").toString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("File not found"), ex.getMessage());
    }

    @Test
    void missingFileParameterThrowsError() {
        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(null), message -> {}));
    }
}
