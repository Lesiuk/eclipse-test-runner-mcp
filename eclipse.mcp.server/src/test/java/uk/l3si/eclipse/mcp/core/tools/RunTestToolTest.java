package uk.l3si.eclipse.mcp.core.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import uk.l3si.eclipse.mcp.tools.Args;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunTestToolTest {

    private static RunTestTool createTool() {
        return new RunTestTool(Map.of("run", "Run tests"), null);
    }

    @Test
    void nameIsCorrect() {
        assertEquals("run_test", createTool().getName());
    }

    @Test
    void schemaIncludesMethodsParameter() {
        RunTestTool tool = createTool();
        assertTrue(tool.getInputSchema().getPropertyNames().contains("method"),
                "Schema should include 'method' property");
        assertTrue(tool.getInputSchema().getPropertyNames().contains("methods"),
                "Schema should include 'methods' property");
    }

    @Test
    void resolveMethods_methodOnly_returnsSingletonList() {
        JsonObject json = new JsonObject();
        json.addProperty("method", "testAdd");
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testAdd"), result);
    }

    @Test
    void resolveMethods_methodsOnly_returnsList() {
        JsonObject json = new JsonObject();
        JsonArray arr = new JsonArray();
        arr.add("testAdd");
        arr.add("testSubtract");
        json.add("methods", arr);
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testAdd", "testSubtract"), result);
    }

    @Test
    void resolveMethods_bothMergedAndDeduplicated() {
        JsonObject json = new JsonObject();
        json.addProperty("method", "testA");
        JsonArray arr = new JsonArray();
        arr.add("testB");
        arr.add("testA");
        json.add("methods", arr);
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testA", "testB"), result);
    }

    @Test
    void resolveMethods_neitherProvided_returnsNull() {
        JsonObject json = new JsonObject();
        assertNull(RunTestTool.resolveMethods(new Args(json)));
    }

    @Test
    void resolveMethods_emptyArrayNoMethod_returnsNull() {
        JsonObject json = new JsonObject();
        json.add("methods", new JsonArray());
        assertNull(RunTestTool.resolveMethods(new Args(json)));
    }

    @Test
    void resolveMethods_preservesOrderMethodFirst() {
        JsonObject json = new JsonObject();
        json.addProperty("method", "testC");
        JsonArray arr = new JsonArray();
        arr.add("testA");
        arr.add("testB");
        json.add("methods", arr);
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testC", "testA", "testB"), result);
    }
}
