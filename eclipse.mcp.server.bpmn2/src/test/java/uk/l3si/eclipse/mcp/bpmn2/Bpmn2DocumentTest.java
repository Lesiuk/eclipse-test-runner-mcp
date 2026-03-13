package uk.l3si.eclipse.mcp.bpmn2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Bpmn2DocumentTest {

    @TempDir
    Path tempDir;

    private Path copyTestResource() throws IOException {
        Path target = tempDir.resolve("test-flow.bpmn2");
        try (InputStream in = getClass().getResourceAsStream("/test-flow.bpmn2")) {
            assertNotNull(in, "Test resource not found");
            Files.copy(in, target);
        }
        return target;
    }

    // ---- parse() tests ----

    @Test
    void parseValidFile() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        assertNotNull(doc.getProcessElement());
        assertNotNull(doc.getDefinitionsElement());
        assertNotNull(doc.getDiagramPlane());
    }

    @Test
    void parseNonExistentFileThrows() {
        String path = tempDir.resolve("does-not-exist.bpmn2").toString();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Bpmn2Document.parse(path));
        assertTrue(ex.getMessage().contains("File not found"), ex.getMessage());
    }

    @Test
    void parseInvalidXmlThrows() throws Exception {
        Path file = tempDir.resolve("bad.bpmn2");
        Files.writeString(file, "this is not xml");
        assertThrows(IllegalArgumentException.class, () -> Bpmn2Document.parse(file.toString()));
    }

    // ---- listNodes() ----

    @Test
    void listNodesReturnsThreeNodes() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        List<Element> nodes = doc.listNodes();
        assertEquals(3, nodes.size());

        List<String> ids = nodes.stream().map(e -> e.getAttribute("id")).toList();
        assertTrue(ids.contains("StartEvent_1"));
        assertTrue(ids.contains("Task_1"));
        assertTrue(ids.contains("EndEvent_1"));
    }

    // ---- listFlows() ----

    @Test
    void listFlowsReturnsTwoFlows() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        List<Element> flows = doc.listFlows();
        assertEquals(2, flows.size());

        List<String> ids = flows.stream().map(e -> e.getAttribute("id")).toList();
        assertTrue(ids.contains("SequenceFlow_1"));
        assertTrue(ids.contains("SequenceFlow_2"));
    }

    // ---- listVariables() ----

    @Test
    void listVariablesReturnsOneVariable() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        List<Element> vars = doc.listVariables();
        assertEquals(1, vars.size());
        assertEquals("myVar", vars.get(0).getAttribute("name"));
    }

    // ---- listSignals() ----

    @Test
    void listSignalsReturnsOneSignal() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        List<Element> signals = doc.listSignals();
        assertEquals(1, signals.size());
        assertEquals("Signal_1", signals.get(0).getAttribute("id"));
    }

    // ---- findNodeById() ----

    @Test
    void findNodeByIdReturnsTask() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element task = doc.findNodeById("Task_1");
        assertNotNull(task);
        assertEquals("task", task.getLocalName());
    }

    @Test
    void findNodeByIdReturnsNullForNonexistent() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        assertNull(doc.findNodeById("nonexistent"));
    }

    // ---- requireNodeExists() ----

    @Test
    void requireNodeExistsReturnsElement() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element task = doc.requireNodeExists("Task_1");
        assertNotNull(task);
        assertEquals("task", task.getLocalName());
    }

    @Test
    void requireNodeExistsThrowsForMissing() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doc.requireNodeExists("Task_99"));
        assertTrue(ex.getMessage().contains("Node not found"), ex.getMessage());
    }

    // ---- requireFlowExists() ----

    @Test
    void requireFlowExistsReturnsElement() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element flow = doc.requireFlowExists("SequenceFlow_1");
        assertNotNull(flow);
        assertEquals("sequenceFlow", flow.getLocalName());
    }

    @Test
    void requireFlowExistsThrowsForMissing() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> doc.requireFlowExists("nope"));
        assertTrue(ex.getMessage().contains("Sequence flow not found"), ex.getMessage());
    }

    // ---- createElement() ----

    @Test
    void createElementAddsChild() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element process = doc.getProcessElement();
        int before = process.getChildNodes().getLength();
        Element el = doc.createElement(process, Bpmn2Document.NS_BPMN2, "endEvent");
        assertNotNull(el);
        assertEquals(Bpmn2Document.NS_BPMN2, el.getNamespaceURI());
        assertEquals("endEvent", el.getLocalName());
        assertTrue(process.getChildNodes().getLength() > before);
    }

    // ---- createTextElement() ----

    @Test
    void createTextElementAddsChildWithText() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element process = doc.getProcessElement();
        Element el = doc.createTextElement(process, Bpmn2Document.NS_BPMN2, "script", "return 42;");
        assertNotNull(el);
        assertEquals("return 42;", el.getTextContent());
    }

    // ---- removeElement() ----

    @Test
    void removeElementRemovesFromParent() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        Element task = doc.findNodeById("Task_1");
        assertNotNull(task);
        doc.removeElement(task);
        assertNull(doc.findNodeById("Task_1"));
    }

    // ---- generateId() ----

    @Test
    void generateIdReturnsNextAvailable() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        // Task_1 exists, so next should be Task_2
        assertEquals("Task_2", doc.generateId("Task"));
    }

    // ---- create() ----

    @Test
    void createNewFileCreatesValidBpmn2() throws Exception {
        Path file = tempDir.resolve("new-flow.bpmn2");

        Bpmn2Document doc = Bpmn2Document.create(
                file.toString(), "com.example.new_flow", "new_flow", "com.example");

        assertTrue(Files.exists(file));
        assertNotNull(doc.getProcessElement());
        assertNotNull(doc.getDefinitionsElement());
        assertNotNull(doc.getDiagramPlane());
        assertEquals("com.example.new_flow", doc.getProcessElement().getAttribute("id"));
        assertEquals("new_flow", doc.getProcessElement().getAttribute("name"));
        assertEquals("true", doc.getProcessElement().getAttribute("isExecutable"));
        assertEquals("Private", doc.getProcessElement().getAttribute("processType"));

        // Verify itemDefinitions have isCollection="false"
        NodeList itemDefs = doc.getDefinitionsElement()
                .getElementsByTagNameNS(Bpmn2Document.NS_BPMN2, "itemDefinition");
        assertTrue(itemDefs.getLength() > 0, "Should have itemDefinitions");
        for (int i = 0; i < itemDefs.getLength(); i++) {
            Element itemDef = (Element) itemDefs.item(i);
            assertEquals("false", itemDef.getAttribute("isCollection"),
                    "itemDefinition " + itemDef.getAttribute("id") + " should have isCollection=false");
        }

        // Verify the file can be re-parsed
        Bpmn2Document reparsed = Bpmn2Document.parse(file.toString());
        assertNotNull(reparsed.getProcessElement());
        assertEquals("com.example.new_flow", reparsed.getProcessElement().getAttribute("id"));
    }

    @Test
    void createExistingFileThrows() throws Exception {
        Path file = copyTestResource();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> Bpmn2Document.create(
                        file.toString(), "com.example.flow", "flow", "com.example"));
        assertTrue(ex.getMessage().contains("File already exists"), ex.getMessage());
    }

    // ---- save() round-trip ----

    @Test
    void saveRoundTripPreservesModification() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        // Add a new endEvent
        Element newEnd = doc.createElement(doc.getProcessElement(), Bpmn2Document.NS_BPMN2, "endEvent");
        newEnd.setAttribute("id", "EndEvent_2");
        newEnd.setAttribute("name", "AnotherEnd");
        doc.save();

        // Re-parse and verify
        Bpmn2Document reparsed = Bpmn2Document.parse(file.toString());
        Element found = reparsed.findNodeById("EndEvent_2");
        assertNotNull(found);
        assertEquals("endEvent", found.getLocalName());
        assertEquals("AnotherEnd", found.getAttribute("name"));
    }

    // ---- clearDiagram() ----

    @Test
    void clearDiagramRemovesAllChildren() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());

        assertNotNull(doc.getDiagramPlane());
        assertTrue(doc.getDiagramPlane().getChildNodes().getLength() > 0);

        doc.clearDiagram();
        // Verify no element children remain
        NodeList children = doc.getDiagramPlane().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            assertFalse(children.item(i) instanceof Element,
                    "Expected no element children after clearDiagram()");
        }
    }

    // ---- addShape() ----

    @Test
    void addShapeAddsBpmnShapeWithBounds() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        doc.clearDiagram();

        doc.addShape("Task_1", 100, 200, 110, 50);

        // Find the shape in the diagram plane
        Element plane = doc.getDiagramPlane();
        Element shape = findChildById(plane, "BPMNShape_Task_1");
        assertNotNull(shape, "BPMNShape should have been created");
        assertEquals("Task_1", shape.getAttribute("bpmnElement"));
        assertEquals(Bpmn2Document.NS_BPMNDI, shape.getNamespaceURI());

        // Check bounds - first Bounds is the shape bounds, second is the label bounds
        NodeList boundsNodes = shape.getElementsByTagNameNS(Bpmn2Document.NS_DC, "Bounds");
        assertTrue(boundsNodes.getLength() >= 1);
        Element bounds = (Element) boundsNodes.item(0);
        assertEquals("100", bounds.getAttribute("x"));
        assertEquals("200", bounds.getAttribute("y"));
        assertEquals("110", bounds.getAttribute("width"));
        assertEquals("50", bounds.getAttribute("height"));
    }

    // ---- addEdge() ----

    @Test
    void addEdgeAddsBpmnEdgeWithWaypoints() throws Exception {
        Path file = copyTestResource();
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        doc.clearDiagram();

        List<double[]> waypoints = List.of(
                new double[]{100, 200},
                new double[]{100, 300},
                new double[]{200, 300}
        );
        doc.addEdge("SequenceFlow_1", waypoints);

        Element plane = doc.getDiagramPlane();
        Element edge = findChildById(plane, "BPMNEdge_SequenceFlow_1");
        assertNotNull(edge, "BPMNEdge should have been created");
        assertEquals("SequenceFlow_1", edge.getAttribute("bpmnElement"));

        NodeList wps = edge.getElementsByTagNameNS(Bpmn2Document.NS_DI, "waypoint");
        assertEquals(3, wps.getLength());
        assertEquals("100", ((Element) wps.item(0)).getAttribute("x"));
        assertEquals("200", ((Element) wps.item(0)).getAttribute("y"));
    }

    // ---- Helper for tests ----

    private Element findChildById(Element parent, String id) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }
}
