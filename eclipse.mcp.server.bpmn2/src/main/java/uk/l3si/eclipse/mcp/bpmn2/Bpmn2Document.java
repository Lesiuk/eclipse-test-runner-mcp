package uk.l3si.eclipse.mcp.bpmn2;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Central class for BPMN2 XML manipulation. Provides parsing, querying,
 * mutation, and serialization of BPMN2 process files using namespace-aware
 * DOM operations.
 */
public class Bpmn2Document {

    public static final String NS_BPMN2 = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String NS_BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    public static final String NS_DC = "http://www.omg.org/spec/DD/20100524/DC";
    public static final String NS_DI = "http://www.omg.org/spec/DD/20100524/DI";
    public static final String NS_TNS = "http://www.jboss.org/drools";
    public static final String NS_XSI = "http://www.w3.org/2001/XMLSchema-instance";

    private static final Set<String> NODE_TYPES = Set.of(
            "startEvent", "endEvent", "task", "scriptTask",
            "userTask", "callActivity", "exclusiveGateway");

    private final Document doc;
    private final Element definitions;
    private final Element process;
    private final Element diagramPlane;
    private final String filePath;
    private final Bpmn2IdGenerator idGenerator;

    private Bpmn2Document(Document doc, Element definitions, Element process,
                          Element diagramPlane, String filePath, Bpmn2IdGenerator idGenerator) {
        this.doc = doc;
        this.definitions = definitions;
        this.process = process;
        this.diagramPlane = diagramPlane;
        this.filePath = filePath;
        this.idGenerator = idGenerator;
    }

    // ---- Static factory methods ----

    /**
     * Parses an existing BPMN2 file into a Bpmn2Document.
     *
     * @param filePath absolute path to the .bpmn2 file
     * @return a new Bpmn2Document
     * @throws IllegalArgumentException if the file does not exist or is not valid BPMN2
     */
    public static Bpmn2Document parse(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File not found: '" + filePath + "'");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(file);

            Element root = doc.getDocumentElement();
            if (!NS_BPMN2.equals(root.getNamespaceURI()) || !"definitions".equals(root.getLocalName())) {
                throw new IllegalArgumentException(
                        "Invalid BPMN2 file: root element must be bpmn2:definitions");
            }

            Element process = findFirstChildElement(root, NS_BPMN2, "process");
            if (process == null) {
                throw new IllegalArgumentException(
                        "Invalid BPMN2 file: no bpmn2:process element found");
            }

            Element diagramPlane = findDiagramPlane(root);
            Bpmn2IdGenerator idGenerator = new Bpmn2IdGenerator(doc);

            return new Bpmn2Document(doc, root, process, diagramPlane, filePath, idGenerator);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse BPMN2 file: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new BPMN2 file with boilerplate XML.
     *
     * @param filePath    absolute path for the new file
     * @param processId   process identifier
     * @param processName human-readable name
     * @param packageName package name
     * @return a new Bpmn2Document
     * @throws IllegalArgumentException if the file already exists or the parent directory does not exist
     */
    public static Bpmn2Document create(String filePath, String processId,
                                       String processName, String packageName) {
        File file = new File(filePath);
        if (file.exists()) {
            throw new IllegalArgumentException(
                    "File already exists: '" + filePath
                            + "'. Use 'bpmn2_get_process' to read it, or choose a different path.");
        }
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            throw new IllegalArgumentException(
                    "Parent directory does not exist: '" + parentDir.getAbsolutePath() + "'");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().newDocument();

            // Create definitions root element
            Element definitions = doc.createElementNS(NS_BPMN2, "bpmn2:definitions");
            definitions.setAttribute("xmlns:bpmn2", NS_BPMN2);
            definitions.setAttribute("xmlns:bpmndi", NS_BPMNDI);
            definitions.setAttribute("xmlns:dc", NS_DC);
            definitions.setAttribute("xmlns:di", NS_DI);
            definitions.setAttribute("xmlns:tns", NS_TNS);
            definitions.setAttribute("xmlns:xsi", NS_XSI);
            definitions.setAttribute("id", "Definitions_1");
            definitions.setAttribute("targetNamespace", NS_TNS);
            doc.appendChild(definitions);

            // Default itemDefinitions
            String[][] itemDefs = {
                    {"ItemDefinition_1", "java.lang.String"},
                    {"ItemDefinition_2", "java.lang.Integer"},
                    {"ItemDefinition_3", "java.lang.Boolean"},
                    {"ItemDefinition_4", "java.lang.Object"}
            };
            for (String[] def : itemDefs) {
                Element itemDef = doc.createElementNS(NS_BPMN2, "bpmn2:itemDefinition");
                itemDef.setAttribute("id", def[0]);
                itemDef.setAttribute("isCollection", "false");
                itemDef.setAttribute("structureRef", def[1]);
                definitions.appendChild(itemDef);
            }

            // Empty process
            Element process = doc.createElementNS(NS_BPMN2, "bpmn2:process");
            process.setAttribute("id", processId);
            process.setAttribute("name", processName);
            process.setAttributeNS(NS_TNS, "tns:packageName", packageName);
            process.setAttribute("isExecutable", "true");
            process.setAttribute("processType", "Private");
            definitions.appendChild(process);

            // BPMNDiagram with BPMNPlane
            Element diagram = doc.createElementNS(NS_BPMNDI, "bpmndi:BPMNDiagram");
            diagram.setAttribute("id", "BPMNDiagram_1");
            diagram.setAttribute("name", processName);
            definitions.appendChild(diagram);

            Element plane = doc.createElementNS(NS_BPMNDI, "bpmndi:BPMNPlane");
            plane.setAttribute("id", "BPMNPlane_1");
            plane.setAttribute("bpmnElement", processId);
            diagram.appendChild(plane);

            Bpmn2IdGenerator idGenerator = new Bpmn2IdGenerator(doc);

            Bpmn2Document bpmn2Doc = new Bpmn2Document(
                    doc, definitions, process, plane, filePath, idGenerator);
            bpmn2Doc.save();
            return bpmn2Doc;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to create BPMN2 file: " + e.getMessage(), e);
        }
    }

    // ---- Query methods ----

    public Element getProcessElement() {
        return process;
    }

    public Element getDefinitionsElement() {
        return definitions;
    }

    public Element getDiagramPlane() {
        return diagramPlane;
    }

    /**
     * Finds a flow node by ID among direct children of the process element.
     * Only searches elements whose local name is in {@link #NODE_TYPES}.
     *
     * @param id the element ID to search for
     * @return the matching Element, or null if not found
     */
    public Element findNodeById(String id) {
        for (Element el : directChildren(process, NS_BPMN2)) {
            if (NODE_TYPES.contains(el.getLocalName()) && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    /**
     * Finds a sequence flow by ID among direct children of the process element.
     *
     * @param id the flow ID to search for
     * @return the matching Element, or null if not found
     */
    public Element findFlowById(String id) {
        for (Element el : directChildren(process, NS_BPMN2)) {
            if ("sequenceFlow".equals(el.getLocalName()) && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    /**
     * Returns all flow nodes that are direct children of the process element.
     */
    public List<Element> listNodes() {
        List<Element> result = new ArrayList<>();
        for (Element el : directChildren(process, NS_BPMN2)) {
            if (NODE_TYPES.contains(el.getLocalName())) {
                result.add(el);
            }
        }
        return result;
    }

    /**
     * Returns all sequence flows that are direct children of the process element.
     */
    public List<Element> listFlows() {
        List<Element> result = new ArrayList<>();
        for (Element el : directChildren(process, NS_BPMN2)) {
            if ("sequenceFlow".equals(el.getLocalName())) {
                result.add(el);
            }
        }
        return result;
    }

    /**
     * Returns all property elements (process variables) that are direct children
     * of the process element.
     */
    public List<Element> listVariables() {
        List<Element> result = new ArrayList<>();
        for (Element el : directChildren(process, NS_BPMN2)) {
            if ("property".equals(el.getLocalName())) {
                result.add(el);
            }
        }
        return result;
    }

    /**
     * Returns all signal elements that are direct children of the definitions element.
     */
    public List<Element> listSignals() {
        List<Element> result = new ArrayList<>();
        for (Element el : directChildren(definitions, NS_BPMN2)) {
            if ("signal".equals(el.getLocalName())) {
                result.add(el);
            }
        }
        return result;
    }

    // ---- Validation helpers ----

    /**
     * Returns the node element with the given ID, or throws if not found.
     *
     * @param id the node ID
     * @return the Element
     * @throws IllegalArgumentException if the node does not exist
     */
    public Element requireNodeExists(String id) {
        Element node = findNodeById(id);
        if (node == null) {
            throw new IllegalArgumentException(
                    "Node not found: '" + id + "'. Use 'bpmn2_get_process' to see available nodes.");
        }
        return node;
    }

    /**
     * Returns the sequence flow element with the given ID, or throws if not found.
     *
     * @param id the flow ID
     * @return the Element
     * @throws IllegalArgumentException if the flow does not exist
     */
    public Element requireFlowExists(String id) {
        Element flow = findFlowById(id);
        if (flow == null) {
            throw new IllegalArgumentException(
                    "Sequence flow not found: '" + id
                            + "'. Use 'bpmn2_get_process' to see available flows.");
        }
        return flow;
    }

    // ---- Mutation helpers ----

    /**
     * Creates a new namespace-aware element with the correct prefix and appends it
     * to the given parent.
     *
     * @param parent    the parent element
     * @param ns        the namespace URI
     * @param localName the local element name
     * @return the newly created element
     */
    public Element createElement(Element parent, String ns, String localName) {
        String prefix = prefixForNamespace(ns);
        String qualifiedName = prefix != null ? prefix + ":" + localName : localName;
        Element el = doc.createElementNS(ns, qualifiedName);
        parent.appendChild(el);
        return el;
    }

    /**
     * Creates a new namespace-aware element with text content and appends it
     * to the given parent.
     *
     * @param parent    the parent element
     * @param ns        the namespace URI
     * @param localName the local element name
     * @param text      the text content
     * @return the newly created element
     */
    public Element createTextElement(Element parent, String ns, String localName, String text) {
        Element el = createElement(parent, ns, localName);
        el.setTextContent(text);
        return el;
    }

    /**
     * Creates a new namespace-aware element with CDATA text content and appends it
     * to the given parent.
     *
     * @param parent    the parent element
     * @param ns        the namespace URI
     * @param localName the local element name
     * @param text      the text content to wrap in CDATA
     * @return the newly created element
     */
    public Element createCDataTextElement(Element parent, String ns, String localName, String text) {
        Element el = createElement(parent, ns, localName);
        el.appendChild(doc.createCDATASection(text));
        return el;
    }

    /**
     * Removes an element from its parent.
     *
     * @param el the element to remove
     */
    public void removeElement(Element el) {
        Node parent = el.getParentNode();
        if (parent != null) {
            parent.removeChild(el);
        }
    }

    /**
     * Generates the next available ID for the given prefix.
     *
     * @param prefix the element type prefix (e.g. "Task", "SequenceFlow")
     * @return a unique ID string
     */
    public String generateId(String prefix) {
        return idGenerator.generate(prefix);
    }

    // ---- Save ----

    /**
     * Writes the DOM document to the file at {@link #filePath} with
     * UTF-8 encoding and 2-space indentation.
     */
    public void save() {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save BPMN2 file: " + e.getMessage(), e);
        }
    }

    // ---- Diagram methods ----

    /**
     * Removes all children of the diagram plane element.
     */
    public void clearDiagram() {
        if (diagramPlane == null) {
            return;
        }
        while (diagramPlane.getFirstChild() != null) {
            diagramPlane.removeChild(diagramPlane.getFirstChild());
        }
    }

    /**
     * Adds a BPMNShape element with dc:Bounds to the diagram plane.
     *
     * @param elementId the BPMN element ID this shape represents
     * @param x         x coordinate
     * @param y         y coordinate
     * @param width     shape width
     * @param height    shape height
     */
    public void addShape(String elementId, double x, double y, double width, double height) {
        addShape(elementId, x, y, width, height, null);
    }

    /**
     * Adds a BPMNShape element with dc:Bounds and optional type-specific attributes
     * to the diagram plane.
     *
     * @param elementId the BPMN element ID this shape represents
     * @param x         x coordinate
     * @param y         y coordinate
     * @param width     shape width
     * @param height    shape height
     * @param nodeType  the BPMN node type (e.g. "task", "exclusiveGateway") for DI attributes
     */
    public void addShape(String elementId, double x, double y, double width, double height,
                         String nodeType) {
        if (diagramPlane == null) {
            return;
        }
        Element shape = createElement(diagramPlane, NS_BPMNDI, "BPMNShape");
        shape.setAttribute("id", "BPMNShape_" + elementId);
        shape.setAttribute("bpmnElement", elementId);

        if (nodeType != null) {
            switch (nodeType) {
                case "callActivity", "task", "scriptTask", "userTask" ->
                        shape.setAttribute("isExpanded", "true");
                case "exclusiveGateway" ->
                        shape.setAttribute("isMarkerVisible", "true");
            }
        }

        Element bounds = createElement(shape, NS_DC, "Bounds");
        bounds.setAttribute("height", formatCoord(height));
        bounds.setAttribute("width", formatCoord(width));
        bounds.setAttribute("x", formatCoord(x));
        bounds.setAttribute("y", formatCoord(y));

        // Add BPMNLabel with bounds positioned below the shape
        Element label = createElement(shape, NS_BPMNDI, "BPMNLabel");
        label.setAttribute("id", "BPMNLabel_" + elementId);
        Element labelBounds = createElement(label, NS_DC, "Bounds");
        double labelWidth = Math.max(20, width);
        double labelHeight = 14;
        double labelX = x + (width - labelWidth) / 2.0;
        double labelY = y + height;
        labelBounds.setAttribute("height", formatCoord(labelHeight));
        labelBounds.setAttribute("width", formatCoord(labelWidth));
        labelBounds.setAttribute("x", formatCoord(labelX));
        labelBounds.setAttribute("y", formatCoord(labelY));
    }

    /**
     * Adds a BPMNEdge element with di:waypoint elements to the diagram plane.
     *
     * @param flowId    the sequence flow ID this edge represents
     * @param waypoints list of [x, y] coordinate pairs
     */
    public void addEdge(String flowId, List<double[]> waypoints) {
        addEdge(flowId, waypoints, null, null);
    }

    /**
     * Adds a BPMNEdge element with di:waypoint elements to the diagram plane,
     * including source and target element references.
     *
     * @param flowId          the sequence flow ID this edge represents
     * @param waypoints       list of [x, y] coordinate pairs
     * @param sourceElementId the source node ID (for sourceElement attribute)
     * @param targetElementId the target node ID (for targetElement attribute)
     */
    public void addEdge(String flowId, List<double[]> waypoints,
                        String sourceElementId, String targetElementId) {
        if (diagramPlane == null) {
            return;
        }
        Element edge = createElement(diagramPlane, NS_BPMNDI, "BPMNEdge");
        edge.setAttribute("id", "BPMNEdge_" + flowId);
        edge.setAttribute("bpmnElement", flowId);
        if (sourceElementId != null) {
            edge.setAttribute("sourceElement", "BPMNShape_" + sourceElementId);
        }
        if (targetElementId != null) {
            edge.setAttribute("targetElement", "BPMNShape_" + targetElementId);
        }

        for (double[] wp : waypoints) {
            Element waypoint = createElement(edge, NS_DI, "waypoint");
            waypoint.setAttributeNS(NS_XSI, "xsi:type", "dc:Point");
            waypoint.setAttribute("x", formatCoord(wp[0]));
            waypoint.setAttribute("y", formatCoord(wp[1]));
        }
    }

    /**
     * Adds a BPMNLabel with dc:Bounds to an existing BPMNEdge or BPMNShape element.
     *
     * @param edgeOrShapeId the ID of the existing edge or shape (e.g. "BPMNEdge_SequenceFlow_1")
     * @param x             label x coordinate
     * @param y             label y coordinate
     * @param text          the label text (not stored in DI, just used for sizing reference)
     */
    public void addLabel(String edgeOrShapeId, double x, double y, String text) {
        if (diagramPlane == null) {
            return;
        }
        // Find the edge or shape element by ID
        Element target = findDiagramElementById(edgeOrShapeId);
        if (target == null) {
            return;
        }

        Element label = createElement(target, NS_BPMNDI, "BPMNLabel");
        Element bounds = createElement(label, NS_DC, "Bounds");
        // Estimate label width/height from text length
        double width = Math.max(20, text.length() * 7.0);
        double height = 14;
        bounds.setAttribute("x", formatCoord(x));
        bounds.setAttribute("y", formatCoord(y));
        bounds.setAttribute("width", formatCoord(width));
        bounds.setAttribute("height", formatCoord(height));
    }

    // ---- Private helpers ----

    /**
     * Maps a namespace URI to its conventional prefix string.
     */
    private String prefixForNamespace(String ns) {
        if (NS_BPMN2.equals(ns)) return "bpmn2";
        if (NS_BPMNDI.equals(ns)) return "bpmndi";
        if (NS_DC.equals(ns)) return "dc";
        if (NS_DI.equals(ns)) return "di";
        if (NS_TNS.equals(ns)) return "tns";
        if (NS_XSI.equals(ns)) return "xsi";
        return null;
    }

    /**
     * Returns direct child elements of the given parent that belong to the
     * specified namespace URI.
     */
    private static List<Element> directChildren(Element parent, String namespaceURI) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && namespaceURI.equals(el.getNamespaceURI())) {
                result.add(el);
            }
        }
        return result;
    }

    /**
     * Finds the first direct child element with the given namespace and local name.
     */
    private static Element findFirstChildElement(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    /**
     * Locates the BPMNPlane element within a BPMNDiagram.
     */
    private static Element findDiagramPlane(Element definitions) {
        Element diagram = findFirstChildElement(definitions, NS_BPMNDI, "BPMNDiagram");
        if (diagram == null) {
            return null;
        }
        return findFirstChildElement(diagram, NS_BPMNDI, "BPMNPlane");
    }

    /**
     * Finds a diagram element (BPMNShape or BPMNEdge) by its ID attribute
     * within the diagram plane.
     */
    private Element findDiagramElementById(String id) {
        if (diagramPlane == null) {
            return null;
        }
        NodeList children = diagramPlane.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    /**
     * Formats a coordinate value, using integer format for whole numbers.
     */
    private static String formatCoord(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }
}
