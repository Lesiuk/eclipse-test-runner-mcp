package uk.l3si.eclipse.mcp.bpmn2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Bpmn2LayoutEngineTest {

    @TempDir
    Path tempDir;

    private Bpmn2LayoutEngine engine = new Bpmn2LayoutEngine();

    // ---- Helper methods ----

    /**
     * Creates a fresh BPMN2 document for testing.
     */
    private Bpmn2Document createDoc(String name) {
        Path file = tempDir.resolve(name + ".bpmn2");
        return Bpmn2Document.create(file.toString(),
                "com.example." + name, name, "com.example");
    }

    /**
     * Adds a node to the process directly for test setup.
     */
    private Element addNode(Bpmn2Document doc, String id, String type) {
        Element node = doc.createElement(doc.getProcessElement(),
                Bpmn2Document.NS_BPMN2, type);
        node.setAttribute("id", id);
        node.setAttribute("name", id);
        if ("exclusiveGateway".equals(type)) {
            // default direction set by caller via setDirection
        }
        return node;
    }

    private void setDirection(Element gateway, String direction) {
        String capitalized = direction.substring(0, 1).toUpperCase() + direction.substring(1);
        gateway.setAttribute("gatewayDirection", capitalized);
    }

    /**
     * Adds a sequence flow between two nodes.
     */
    private void addFlow(Bpmn2Document doc, String id, String sourceId, String targetId) {
        addFlow(doc, id, sourceId, targetId, null);
    }

    private void addFlow(Bpmn2Document doc, String id, String sourceId, String targetId,
                         String name) {
        Element flow = doc.createElement(doc.getProcessElement(),
                Bpmn2Document.NS_BPMN2, "sequenceFlow");
        flow.setAttribute("id", id);
        flow.setAttribute("sourceRef", sourceId);
        flow.setAttribute("targetRef", targetId);
        if (name != null) {
            flow.setAttribute("name", name);
        }

        // Add outgoing/incoming refs
        Element sourceNode = doc.findNodeById(sourceId);
        if (sourceNode != null) {
            doc.createTextElement(sourceNode, Bpmn2Document.NS_BPMN2, "outgoing", id);
        }
        Element targetNode = doc.findNodeById(targetId);
        if (targetNode != null) {
            doc.createTextElement(targetNode, Bpmn2Document.NS_BPMN2, "incoming", id);
        }
    }

    /**
     * Finds a BPMNShape element by bpmnElement attribute.
     */
    private Element findShape(Bpmn2Document doc, String nodeId) {
        Element plane = doc.getDiagramPlane();
        NodeList children = plane.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && "BPMNShape".equals(el.getLocalName())
                    && nodeId.equals(el.getAttribute("bpmnElement"))) {
                return el;
            }
        }
        return null;
    }

    /**
     * Finds a BPMNEdge element by bpmnElement attribute.
     */
    private Element findEdge(Bpmn2Document doc, String flowId) {
        Element plane = doc.getDiagramPlane();
        NodeList children = plane.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && "BPMNEdge".equals(el.getLocalName())
                    && flowId.equals(el.getAttribute("bpmnElement"))) {
                return el;
            }
        }
        return null;
    }

    /**
     * Gets the dc:Bounds element from a shape.
     */
    private Element getBounds(Element shape) {
        NodeList bounds = shape.getElementsByTagNameNS(Bpmn2Document.NS_DC, "Bounds");
        return bounds.getLength() > 0 ? (Element) bounds.item(0) : null;
    }

    /**
     * Gets the x coordinate from a shape's bounds.
     */
    private double getX(Element shape) {
        return Double.parseDouble(getBounds(shape).getAttribute("x"));
    }

    /**
     * Gets the y coordinate from a shape's bounds.
     */
    private double getY(Element shape) {
        return Double.parseDouble(getBounds(shape).getAttribute("y"));
    }

    /**
     * Gets the width from a shape's bounds.
     */
    private double getWidth(Element shape) {
        return Double.parseDouble(getBounds(shape).getAttribute("width"));
    }

    /**
     * Gets the height from a shape's bounds.
     */
    private double getHeight(Element shape) {
        return Double.parseDouble(getBounds(shape).getAttribute("height"));
    }

    /**
     * Counts waypoints in an edge element.
     */
    private int countWaypoints(Element edge) {
        NodeList wps = edge.getElementsByTagNameNS(Bpmn2Document.NS_DI, "waypoint");
        return wps.getLength();
    }

    // ---- Tests ----

    @Test
    void linearLayout_startTaskEnd() {
        Bpmn2Document doc = createDoc("linear");
        addNode(doc, "StartEvent_1", "startEvent");
        addNode(doc, "Task_1", "task");
        addNode(doc, "EndEvent_1", "endEvent");
        addFlow(doc, "SequenceFlow_1", "StartEvent_1", "Task_1");
        addFlow(doc, "SequenceFlow_2", "Task_1", "EndEvent_1");

        int nodesLaid = engine.layout(doc);

        assertEquals(3, nodesLaid);

        // Verify all nodes have shapes
        Element startShape = findShape(doc, "StartEvent_1");
        Element taskShape = findShape(doc, "Task_1");
        Element endShape = findShape(doc, "EndEvent_1");
        assertNotNull(startShape, "StartEvent_1 should have a shape");
        assertNotNull(taskShape, "Task_1 should have a shape");
        assertNotNull(endShape, "EndEvent_1 should have a shape");

        // Verify start is above task is above end (Y increases downward)
        double startY = getY(startShape);
        double taskY = getY(taskShape);
        double endY = getY(endShape);
        assertTrue(startY < taskY, "Start should be above task");
        assertTrue(taskY < endY, "Task should be above end");

        // Verify node dimensions
        assertEquals(36, getWidth(startShape), "Start event width");
        assertEquals(36, getHeight(startShape), "Start event height");
        assertEquals(110, getWidth(taskShape), "Task width");
        assertEquals(50, getHeight(taskShape), "Task height");
        assertEquals(36, getWidth(endShape), "End event width");
        assertEquals(36, getHeight(endShape), "End event height");

        // Verify no overlaps
        double startBottom = startY + getHeight(startShape);
        assertTrue(startBottom <= taskY, "Start and task should not overlap");
        double taskBottom = taskY + getHeight(taskShape);
        assertTrue(taskBottom <= endY, "Task and end should not overlap");
    }

    @Test
    void simpleBranch_divergeConverge() {
        Bpmn2Document doc = createDoc("branch");
        addNode(doc, "StartEvent_1", "startEvent");
        Element diverge = addNode(doc, "ExclusiveGateway_1", "exclusiveGateway");
        setDirection(diverge, "diverging");
        addNode(doc, "Task_1", "task");
        addNode(doc, "Task_2", "task");
        Element converge = addNode(doc, "ExclusiveGateway_2", "exclusiveGateway");
        setDirection(converge, "converging");
        addNode(doc, "EndEvent_1", "endEvent");

        addFlow(doc, "SequenceFlow_1", "StartEvent_1", "ExclusiveGateway_1");
        addFlow(doc, "SequenceFlow_2", "ExclusiveGateway_1", "Task_1", "YES");
        addFlow(doc, "SequenceFlow_3", "ExclusiveGateway_1", "Task_2", "NO");
        addFlow(doc, "SequenceFlow_4", "Task_1", "ExclusiveGateway_2");
        addFlow(doc, "SequenceFlow_5", "Task_2", "ExclusiveGateway_2");
        addFlow(doc, "SequenceFlow_6", "ExclusiveGateway_2", "EndEvent_1");

        int nodesLaid = engine.layout(doc);

        assertEquals(6, nodesLaid);

        // All shapes exist
        assertNotNull(findShape(doc, "StartEvent_1"));
        assertNotNull(findShape(doc, "ExclusiveGateway_1"));
        assertNotNull(findShape(doc, "Task_1"));
        assertNotNull(findShape(doc, "Task_2"));
        assertNotNull(findShape(doc, "ExclusiveGateway_2"));
        assertNotNull(findShape(doc, "EndEvent_1"));

        // Diverge is above both tasks
        double divergeY = getY(findShape(doc, "ExclusiveGateway_1"));
        double task1Y = getY(findShape(doc, "Task_1"));
        double task2Y = getY(findShape(doc, "Task_2"));
        assertTrue(divergeY < task1Y, "Diverge should be above Task_1");
        assertTrue(divergeY < task2Y, "Diverge should be above Task_2");

        // Tasks should be at the same Y level (side by side)
        assertEquals(task1Y, task2Y, 0.01, "Branch tasks should be at same Y level");

        // Tasks should be side by side (different X)
        double task1X = getX(findShape(doc, "Task_1"));
        double task2X = getX(findShape(doc, "Task_2"));
        assertNotEquals(task1X, task2X, 0.01, "Branch tasks should be at different X positions");

        // Converge is below both tasks
        double convergeY = getY(findShape(doc, "ExclusiveGateway_2"));
        assertTrue(convergeY > task1Y + Bpmn2LayoutEngine.TASK_HEIGHT,
                "Converge should be below tasks");

        // End is below converge
        double endY = getY(findShape(doc, "EndEvent_1"));
        assertTrue(endY > convergeY + Bpmn2LayoutEngine.GATEWAY_SIZE,
                "End should be below converge");
    }

    @Test
    void nestedBranch() {
        Bpmn2Document doc = createDoc("nested");
        addNode(doc, "StartEvent_1", "startEvent");
        Element diverge1 = addNode(doc, "ExclusiveGateway_1", "exclusiveGateway");
        setDirection(diverge1, "diverging");
        addNode(doc, "Task_1", "task");

        // Nested branch
        Element diverge2 = addNode(doc, "ExclusiveGateway_2", "exclusiveGateway");
        setDirection(diverge2, "diverging");
        addNode(doc, "Task_A", "task");
        addNode(doc, "Task_B", "task");
        Element converge2 = addNode(doc, "ExclusiveGateway_3", "exclusiveGateway");
        setDirection(converge2, "converging");

        Element converge1 = addNode(doc, "ExclusiveGateway_4", "exclusiveGateway");
        setDirection(converge1, "converging");
        addNode(doc, "EndEvent_1", "endEvent");

        // Main flow
        addFlow(doc, "SF_1", "StartEvent_1", "ExclusiveGateway_1");
        // Branch 1: simple task
        addFlow(doc, "SF_2", "ExclusiveGateway_1", "Task_1");
        addFlow(doc, "SF_3", "Task_1", "ExclusiveGateway_4");
        // Branch 2: nested branch
        addFlow(doc, "SF_4", "ExclusiveGateway_1", "ExclusiveGateway_2");
        addFlow(doc, "SF_5", "ExclusiveGateway_2", "Task_A");
        addFlow(doc, "SF_6", "ExclusiveGateway_2", "Task_B");
        addFlow(doc, "SF_7", "Task_A", "ExclusiveGateway_3");
        addFlow(doc, "SF_8", "Task_B", "ExclusiveGateway_3");
        addFlow(doc, "SF_9", "ExclusiveGateway_3", "ExclusiveGateway_4");
        // After converge
        addFlow(doc, "SF_10", "ExclusiveGateway_4", "EndEvent_1");

        int nodesLaid = engine.layout(doc);

        assertEquals(9, nodesLaid);

        // Verify all nodes have shapes
        for (String id : List.of("StartEvent_1", "ExclusiveGateway_1", "Task_1",
                "ExclusiveGateway_2", "Task_A", "Task_B", "ExclusiveGateway_3",
                "ExclusiveGateway_4", "EndEvent_1")) {
            assertNotNull(findShape(doc, id), id + " should have a shape");
        }

        // Nested tasks should be at the same Y level
        double taskAY = getY(findShape(doc, "Task_A"));
        double taskBY = getY(findShape(doc, "Task_B"));
        assertEquals(taskAY, taskBY, 0.01, "Nested branch tasks should be at same Y level");

        // Nested tasks should be at different X positions
        double taskAX = getX(findShape(doc, "Task_A"));
        double taskBX = getX(findShape(doc, "Task_B"));
        assertNotEquals(taskAX, taskBX, 0.01,
                "Nested branch tasks should be at different X positions");

        // Nested diverge above nested tasks
        double diverge2Y = getY(findShape(doc, "ExclusiveGateway_2"));
        assertTrue(diverge2Y < taskAY, "Nested diverge should be above nested tasks");

        // Nested converge below nested tasks
        double converge2Y = getY(findShape(doc, "ExclusiveGateway_3"));
        assertTrue(converge2Y > taskAY + Bpmn2LayoutEngine.TASK_HEIGHT,
                "Nested converge should be below nested tasks");
    }

    @Test
    void nonStructuredGraph_branchesToEnds() {
        Bpmn2Document doc = createDoc("nonstructured");
        addNode(doc, "StartEvent_1", "startEvent");
        Element diverge = addNode(doc, "ExclusiveGateway_1", "exclusiveGateway");
        setDirection(diverge, "diverging");
        addNode(doc, "Task_1", "task");
        addNode(doc, "Task_2", "task");
        addNode(doc, "EndEvent_1", "endEvent");
        addNode(doc, "EndEvent_2", "endEvent");

        addFlow(doc, "SF_1", "StartEvent_1", "ExclusiveGateway_1");
        addFlow(doc, "SF_2", "ExclusiveGateway_1", "Task_1");
        addFlow(doc, "SF_3", "ExclusiveGateway_1", "Task_2");
        addFlow(doc, "SF_4", "Task_1", "EndEvent_1");
        addFlow(doc, "SF_5", "Task_2", "EndEvent_2");

        int nodesLaid = engine.layout(doc);

        assertEquals(6, nodesLaid);

        // All nodes should have shapes
        assertNotNull(findShape(doc, "StartEvent_1"));
        assertNotNull(findShape(doc, "ExclusiveGateway_1"));
        assertNotNull(findShape(doc, "Task_1"));
        assertNotNull(findShape(doc, "Task_2"));
        assertNotNull(findShape(doc, "EndEvent_1"));
        assertNotNull(findShape(doc, "EndEvent_2"));

        // Tasks should be side by side
        double task1X = getX(findShape(doc, "Task_1"));
        double task2X = getX(findShape(doc, "Task_2"));
        assertNotEquals(task1X, task2X, 0.01, "Tasks should be at different X positions");

        // Tasks should be at the same Y level
        double task1Y = getY(findShape(doc, "Task_1"));
        double task2Y = getY(findShape(doc, "Task_2"));
        assertEquals(task1Y, task2Y, 0.01, "Tasks should be at same Y level");

        // End events should be below their respective tasks
        double end1Y = getY(findShape(doc, "EndEvent_1"));
        double end2Y = getY(findShape(doc, "EndEvent_2"));
        assertTrue(end1Y > task1Y + Bpmn2LayoutEngine.TASK_HEIGHT, "End1 below Task1");
        assertTrue(end2Y > task2Y + Bpmn2LayoutEngine.TASK_HEIGHT, "End2 below Task2");
    }

    @Test
    void edgesHaveWaypoints() {
        Bpmn2Document doc = createDoc("edges");
        addNode(doc, "StartEvent_1", "startEvent");
        addNode(doc, "Task_1", "task");
        addNode(doc, "EndEvent_1", "endEvent");
        addFlow(doc, "SequenceFlow_1", "StartEvent_1", "Task_1");
        addFlow(doc, "SequenceFlow_2", "Task_1", "EndEvent_1");

        engine.layout(doc);

        // Verify edges exist for all flows
        Element edge1 = findEdge(doc, "SequenceFlow_1");
        Element edge2 = findEdge(doc, "SequenceFlow_2");
        assertNotNull(edge1, "Edge for SequenceFlow_1 should exist");
        assertNotNull(edge2, "Edge for SequenceFlow_2 should exist");

        // Each edge should have at least 2 waypoints
        assertTrue(countWaypoints(edge1) >= 2,
                "Edge 1 should have at least 2 waypoints, got " + countWaypoints(edge1));
        assertTrue(countWaypoints(edge2) >= 2,
                "Edge 2 should have at least 2 waypoints, got " + countWaypoints(edge2));
    }

    @Test
    void branchEdgesHaveWaypoints() {
        Bpmn2Document doc = createDoc("branchedges");
        addNode(doc, "StartEvent_1", "startEvent");
        Element diverge = addNode(doc, "ExclusiveGateway_1", "exclusiveGateway");
        setDirection(diverge, "diverging");
        addNode(doc, "Task_1", "task");
        addNode(doc, "Task_2", "task");
        Element converge = addNode(doc, "ExclusiveGateway_2", "exclusiveGateway");
        setDirection(converge, "converging");
        addNode(doc, "EndEvent_1", "endEvent");

        addFlow(doc, "SF_1", "StartEvent_1", "ExclusiveGateway_1");
        addFlow(doc, "SF_2", "ExclusiveGateway_1", "Task_1", "YES");
        addFlow(doc, "SF_3", "ExclusiveGateway_1", "Task_2", "NO");
        addFlow(doc, "SF_4", "Task_1", "ExclusiveGateway_2");
        addFlow(doc, "SF_5", "Task_2", "ExclusiveGateway_2");
        addFlow(doc, "SF_6", "ExclusiveGateway_2", "EndEvent_1");

        engine.layout(doc);

        // All edges should exist with waypoints
        for (String flowId : List.of("SF_1", "SF_2", "SF_3", "SF_4", "SF_5", "SF_6")) {
            Element edge = findEdge(doc, flowId);
            assertNotNull(edge, "Edge for " + flowId + " should exist");
            assertTrue(countWaypoints(edge) >= 2,
                    "Edge " + flowId + " should have at least 2 waypoints");
        }
    }

    @Test
    void signalStartEvents_placedSeparately() {
        Bpmn2Document doc = createDoc("signal");
        // Main start event
        addNode(doc, "StartEvent_1", "startEvent");
        addNode(doc, "Task_1", "task");
        addNode(doc, "EndEvent_1", "endEvent");
        addFlow(doc, "SF_1", "StartEvent_1", "Task_1");
        addFlow(doc, "SF_2", "Task_1", "EndEvent_1");

        // Signal start event
        Element signalStart = addNode(doc, "StartEvent_2", "startEvent");
        Element signalEventDef = doc.createElement(signalStart,
                Bpmn2Document.NS_BPMN2, "signalEventDefinition");
        signalEventDef.setAttribute("id", "SED_1");
        signalEventDef.setAttribute("signalRef", "Signal_1");

        int nodesLaid = engine.layout(doc);

        assertEquals(4, nodesLaid);

        // Signal start event should have a shape
        Element signalShape = findShape(doc, "StartEvent_2");
        assertNotNull(signalShape, "Signal start event should have a shape");

        // Signal start event should be to the left of the main start event
        Element mainStartShape = findShape(doc, "StartEvent_1");
        double signalX = getX(signalShape);
        double mainX = getX(mainStartShape);
        assertTrue(signalX < mainX,
                "Signal start event should be to the left of main start. signalX="
                        + signalX + " mainX=" + mainX);
    }

    @Test
    void singleNodeProcess() {
        Bpmn2Document doc = createDoc("single");
        addNode(doc, "StartEvent_1", "startEvent");

        int nodesLaid = engine.layout(doc);

        assertEquals(1, nodesLaid);

        Element shape = findShape(doc, "StartEvent_1");
        assertNotNull(shape, "Single start event should have a shape");
        assertEquals(36, getWidth(shape), "Start event width");
        assertEquals(36, getHeight(shape), "Start event height");
    }

    @Test
    void namedFlowsGetLabels() {
        Bpmn2Document doc = createDoc("labels");
        addNode(doc, "StartEvent_1", "startEvent");
        Element diverge = addNode(doc, "ExclusiveGateway_1", "exclusiveGateway");
        setDirection(diverge, "diverging");
        addNode(doc, "Task_1", "task");
        addNode(doc, "Task_2", "task");
        Element converge = addNode(doc, "ExclusiveGateway_2", "exclusiveGateway");
        setDirection(converge, "converging");

        addFlow(doc, "SF_1", "StartEvent_1", "ExclusiveGateway_1");
        addFlow(doc, "SF_2", "ExclusiveGateway_1", "Task_1", "YES");
        addFlow(doc, "SF_3", "ExclusiveGateway_1", "Task_2", "NO");
        addFlow(doc, "SF_4", "Task_1", "ExclusiveGateway_2");
        addFlow(doc, "SF_5", "Task_2", "ExclusiveGateway_2");

        engine.layout(doc);

        // Edges with names should have BPMNLabel children
        Element edge2 = findEdge(doc, "SF_2");
        assertNotNull(edge2);
        Element label2 = findChildElement(edge2, Bpmn2Document.NS_BPMNDI, "BPMNLabel");
        assertNotNull(label2, "Named flow SF_2 should have a BPMNLabel");

        Element edge3 = findEdge(doc, "SF_3");
        assertNotNull(edge3);
        Element label3 = findChildElement(edge3, Bpmn2Document.NS_BPMNDI, "BPMNLabel");
        assertNotNull(label3, "Named flow SF_3 should have a BPMNLabel");

        // Unnamed flow should NOT have a label
        Element edge1 = findEdge(doc, "SF_1");
        assertNotNull(edge1);
        Element label1 = findChildElement(edge1, Bpmn2Document.NS_BPMNDI, "BPMNLabel");
        assertNull(label1, "Unnamed flow SF_1 should not have a BPMNLabel");
    }

    // ---- Helper ----

    private static Element findChildElement(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }
}
