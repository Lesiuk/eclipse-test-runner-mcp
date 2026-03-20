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

class AddGatewayToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddGatewayTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddGatewayTool();
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
    void nameIsAddGateway() {
        assertEquals("bpmn2_gateway", tool.getName());
    }

    @Test
    void addDivergingGateway() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Check Condition");
        args.addProperty("direction", "diverging");

        JsonObject result = executeAndSerialize(args);

        assertEquals("exclusiveGateway", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("Diverging", node.getAttribute("gatewayDirection"));
    }

    @Test
    void addConvergingGateway() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Merge");
        args.addProperty("direction", "converging");

        JsonObject result = executeAndSerialize(args);

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("Converging", node.getAttribute("gatewayDirection"));
    }

    @Test
    void missingDirectionThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Missing Direction");

        assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
    }

    @Test
    void invalidDirectionThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Bad Direction");
        args.addProperty("direction", "invalid");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Invalid direction"), ex.getMessage());
    }
}
