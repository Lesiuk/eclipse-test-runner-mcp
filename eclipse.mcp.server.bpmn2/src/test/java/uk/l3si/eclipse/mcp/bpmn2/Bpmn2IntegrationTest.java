package uk.l3si.eclipse.mcp.bpmn2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.tools.*;
import uk.l3si.eclipse.mcp.tools.Args;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests exercising the full BPMN2 tool workflow
 * without mocking. Each test creates a process, manipulates it through
 * tool calls, and verifies the final state.
 */
class Bpmn2IntegrationTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    // ---- Tool instances ----

    private final CreateProcessTool createTool = new CreateProcessTool();
    private final GetProcessTool getTool = new GetProcessTool();
    private final AddServiceTaskTool addServiceTaskTool = new AddServiceTaskTool();
    private final AddSubflowCallTool addSubflowCallTool = new AddSubflowCallTool();
    private final AddScriptTaskTool addScriptTaskTool = new AddScriptTaskTool();
    private final AddExtensionPointTool addExtensionPointTool = new AddExtensionPointTool();
    private final AddGatewayTool addGatewayTool = new AddGatewayTool();
    private final AddStartEventTool addStartEventTool = new AddStartEventTool();
    private final AddEndEventTool addEndEventTool = new AddEndEventTool();
    private final UpdateNodeTool updateNodeTool = new UpdateNodeTool();
    private final RemoveNodeTool removeNodeTool = new RemoveNodeTool();
    private final AddFlowTool addFlowTool = new AddFlowTool();
    private final UpdateFlowTool updateFlowTool = new UpdateFlowTool();
    private final RemoveFlowTool removeFlowTool = new RemoveFlowTool();
    private final VariableTool variableTool = new VariableTool();
    private final SignalTool signalTool = new SignalTool();
    private final AutoLayoutTool autoLayoutTool = new AutoLayoutTool();

    // ---- Helpers ----

    private JsonObject exec(Object tool, JsonObject args) throws Exception {
        Object result = ((uk.l3si.eclipse.mcp.tools.McpTool) tool).execute(new Args(args));
        return GSON.toJsonTree(result).getAsJsonObject();
    }

    private JsonObject fileArg(Path file) {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        return args;
    }

    private String createProcess(Path file) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("processId", "com.test.workflow");
        args.addProperty("processName", "workflow");
        args.addProperty("packageName", "com.test");
        JsonObject result = exec(createTool, args);
        return result.get("processId").getAsString();
    }

    private String addStartEvent(Path file, String name) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", name);
        JsonObject result = exec(addStartEventTool, args);
        return result.get("id").getAsString();
    }

    private String addEndEvent(Path file, String name) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", name);
        JsonObject result = exec(addEndEventTool, args);
        return result.get("id").getAsString();
    }

    private String addServiceTask(Path file, String name, String taskName) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", name);
        args.addProperty("taskName", taskName);
        JsonObject result = exec(addServiceTaskTool, args);
        return result.get("id").getAsString();
    }

    private String addScriptTask(Path file, String name, String script) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", name);
        args.addProperty("script", script);
        JsonObject result = exec(addScriptTaskTool, args);
        return result.get("id").getAsString();
    }

    private String addGateway(Path file, String name, String direction) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", name);
        args.addProperty("direction", direction);
        JsonObject result = exec(addGatewayTool, args);
        return result.get("id").getAsString();
    }

    private String addFlow(Path file, String source, String target,
                           String... extraKvPairs) throws Exception {
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("source", source);
        args.addProperty("target", target);
        for (int i = 0; i < extraKvPairs.length; i += 2) {
            args.addProperty(extraKvPairs[i], extraKvPairs[i + 1]);
        }
        JsonObject result = exec(addFlowTool, args);
        return result.get("id").getAsString();
    }

    private int countDiagramElements(Bpmn2Document doc, String localName) {
        Element plane = doc.getDiagramPlane();
        if (plane == null) return 0;
        int count = 0;
        NodeList children = plane.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && localName.equals(el.getLocalName())) {
                count++;
            }
        }
        return count;
    }

    // ---- Tests ----

    @Test
    void fullWorkflow() throws Exception {
        Path file = tempDir.resolve("full-workflow.bpmn2");

        // 1. Create a new process
        createProcess(file);

        // 2. Add nodes using domain-specific tools
        String startId = addStartEvent(file, "Start");
        String taskId = addServiceTask(file, "Process Order", "com.test.ProcessOrder");
        String divergeId = addGateway(file, "Check Amount", "diverging");
        String branch1Id = addServiceTask(file, "Approve", "com.test.Approve");
        String branch2Id = addScriptTask(file, "Log Rejection",
                "System.out.println(\"rejected\");");
        String convergeId = addGateway(file, "Merge", "converging");
        String endId = addEndEvent(file, "End");

        // 3. Add flows connecting them
        String flow1 = addFlow(file, startId, taskId);
        String flow2 = addFlow(file, taskId, divergeId);
        String flow3 = addFlow(file, divergeId, branch1Id,
                "name", "YES", "condition", "return amount > 100;");
        String flow4 = addFlow(file, divergeId, branch2Id,
                "name", "NO", "condition", "return amount <= 100;");
        String flow5 = addFlow(file, branch1Id, convergeId);
        String flow6 = addFlow(file, branch2Id, convergeId);
        String flow7 = addFlow(file, convergeId, endId);

        // 4. Add a variable
        JsonObject addVarArgs = new JsonObject();
        addVarArgs.addProperty("file", file.toString());
        addVarArgs.addProperty("action", "add");
        addVarArgs.addProperty("name", "amount");
        addVarArgs.addProperty("type", "java.lang.Integer");
        JsonObject varResult = exec(variableTool, addVarArgs);
        assertEquals("amount", varResult.get("name").getAsString());

        // 5. Add a signal
        JsonObject addSignalArgs = new JsonObject();
        addSignalArgs.addProperty("file", file.toString());
        addSignalArgs.addProperty("action", "add");
        addSignalArgs.addProperty("name", "com.test:orderReceived");
        JsonObject signalResult = exec(signalTool, addSignalArgs);
        String signalId = signalResult.get("id").getAsString();
        assertNotNull(signalId);

        // 6. Auto-layout
        JsonObject layoutResult = exec(autoLayoutTool, fileArg(file));
        assertEquals(7, layoutResult.get("nodesLaid").getAsInt());

        // 7. Get process and verify everything
        JsonObject processInfo = exec(getTool, fileArg(file));

        assertEquals("com.test.workflow", processInfo.get("processId").getAsString());
        assertEquals("workflow", processInfo.get("processName").getAsString());
        assertEquals("com.test", processInfo.get("packageName").getAsString());

        // Verify nodes
        JsonArray nodes = processInfo.getAsJsonArray("nodes");
        assertEquals(7, nodes.size(), "Expected 7 nodes");

        // Verify node types
        long startEvents = countNodesByType(nodes, "startEvent");
        long endEvents = countNodesByType(nodes, "endEvent");
        long tasks = countNodesByType(nodes, "task");
        long scriptTasks = countNodesByType(nodes, "scriptTask");
        long gateways = countNodesByType(nodes, "exclusiveGateway");
        assertEquals(1, startEvents, "Expected 1 start event");
        assertEquals(1, endEvents, "Expected 1 end event");
        assertEquals(2, tasks, "Expected 2 tasks");
        assertEquals(1, scriptTasks, "Expected 1 script task");
        assertEquals(2, gateways, "Expected 2 gateways");

        // Verify flows
        JsonArray flows = processInfo.getAsJsonArray("flows");
        assertEquals(7, flows.size(), "Expected 7 flows");

        // Verify at least one flow has a condition
        boolean hasCondition = false;
        for (int i = 0; i < flows.size(); i++) {
            JsonObject flow = flows.get(i).getAsJsonObject();
            if (flow.has("condition") && !flow.get("condition").isJsonNull()) {
                hasCondition = true;
                break;
            }
        }
        assertTrue(hasCondition, "At least one flow should have a condition");

        // Verify variable
        JsonArray variables = processInfo.getAsJsonArray("variables");
        assertEquals(1, variables.size(), "Expected 1 variable");
        assertEquals("amount", variables.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("java.lang.Integer",
                variables.get(0).getAsJsonObject().get("type").getAsString());

        // Verify signal
        JsonArray signals = processInfo.getAsJsonArray("signals");
        assertEquals(1, signals.size(), "Expected 1 signal");
        assertEquals("com.test:orderReceived",
                signals.get(0).getAsJsonObject().get("name").getAsString());

        // 8. Re-parse the file and verify diagram has shapes and edges
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        int shapeCount = countDiagramElements(doc, "BPMNShape");
        int edgeCount = countDiagramElements(doc, "BPMNEdge");
        assertEquals(7, shapeCount, "Expected 7 BPMNShapes (one per node)");
        assertEquals(7, edgeCount, "Expected 7 BPMNEdges (one per flow)");
    }

    @Test
    void updateAndRemoveWorkflow() throws Exception {
        Path file = tempDir.resolve("update-remove.bpmn2");

        // Create simple process: start -> task -> end
        createProcess(file);
        String startId = addStartEvent(file, "Start");
        String taskId = addServiceTask(file, "Original Task", "com.test.OriginalTask");
        String endId = addEndEvent(file, "End");
        String flow1 = addFlow(file, startId, taskId);
        String flow2 = addFlow(file, taskId, endId);

        // Update the task name
        JsonObject updateArgs = new JsonObject();
        updateArgs.addProperty("file", file.toString());
        updateArgs.addProperty("id", taskId);
        updateArgs.addProperty("name", "Updated Task");
        JsonObject updateResult = exec(updateNodeTool, updateArgs);
        assertTrue(updateResult.getAsJsonArray("updated").toString().contains("name"));

        // Verify name changed via get_process
        JsonObject processInfo = exec(getTool, fileArg(file));
        JsonArray nodes = processInfo.getAsJsonArray("nodes");
        boolean foundUpdatedName = false;
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject node = nodes.get(i).getAsJsonObject();
            if (taskId.equals(node.get("id").getAsString())) {
                assertEquals("Updated Task", node.get("name").getAsString());
                foundUpdatedName = true;
                break;
            }
        }
        assertTrue(foundUpdatedName, "Should find the updated task");

        // Add another task between existing task and end
        // First remove the flow from task to end
        JsonObject removeFlowArgs = new JsonObject();
        removeFlowArgs.addProperty("file", file.toString());
        removeFlowArgs.addProperty("id", flow2);
        exec(removeFlowTool, removeFlowArgs);

        // Add a new task
        String newTaskId = addServiceTask(file, "New Task", "com.test.NewTask");

        // Connect: task -> newTask -> end
        String flow3 = addFlow(file, taskId, newTaskId);
        String flow4 = addFlow(file, newTaskId, endId);

        // Verify the intermediate state
        processInfo = exec(getTool, fileArg(file));
        nodes = processInfo.getAsJsonArray("nodes");
        assertEquals(4, nodes.size(), "Expected 4 nodes after adding new task");
        JsonArray flows = processInfo.getAsJsonArray("flows");
        assertEquals(3, flows.size(), "Expected 3 flows after reconnecting");

        // Update a flow condition
        JsonObject updateFlowArgs = new JsonObject();
        updateFlowArgs.addProperty("file", file.toString());
        updateFlowArgs.addProperty("id", flow3);
        updateFlowArgs.addProperty("name", "to-new-task");
        JsonObject flowUpdateResult = exec(updateFlowTool, updateFlowArgs);
        assertTrue(flowUpdateResult.getAsJsonArray("updated").toString().contains("name"));

        // Remove the new task (should also remove connected flows)
        JsonObject removeNodeArgs = new JsonObject();
        removeNodeArgs.addProperty("file", file.toString());
        removeNodeArgs.addProperty("id", newTaskId);
        JsonObject removeResult = exec(removeNodeTool, removeNodeArgs);
        assertEquals(newTaskId, removeResult.get("id").getAsString());
        JsonArray removedFlows = removeResult.getAsJsonArray("removedFlows");
        assertEquals(2, removedFlows.size(),
                "Removing node should clean up 2 connected flows");

        // Verify final state: start, task, end with only 1 flow (start->task)
        processInfo = exec(getTool, fileArg(file));
        nodes = processInfo.getAsJsonArray("nodes");
        assertEquals(3, nodes.size(), "Expected 3 nodes after remove");
        flows = processInfo.getAsJsonArray("flows");
        assertEquals(1, flows.size(), "Expected 1 flow after remove");

        // Add variable and then remove it
        JsonObject addVarArgs = new JsonObject();
        addVarArgs.addProperty("file", file.toString());
        addVarArgs.addProperty("action", "add");
        addVarArgs.addProperty("name", "tempVar");
        addVarArgs.addProperty("type", "java.lang.String");
        exec(variableTool, addVarArgs);

        processInfo = exec(getTool, fileArg(file));
        assertEquals(1, processInfo.getAsJsonArray("variables").size());

        JsonObject removeVarArgs = new JsonObject();
        removeVarArgs.addProperty("file", file.toString());
        removeVarArgs.addProperty("action", "remove");
        removeVarArgs.addProperty("name", "tempVar");
        exec(variableTool, removeVarArgs);

        processInfo = exec(getTool, fileArg(file));
        assertEquals(0, processInfo.getAsJsonArray("variables").size(),
                "Variable should be removed");
    }

    // ---- Utility ----

    private long countNodesByType(JsonArray nodes, String type) {
        long count = 0;
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject node = nodes.get(i).getAsJsonObject();
            if (type.equals(node.get("type").getAsString())) {
                count++;
            }
        }
        return count;
    }
}
