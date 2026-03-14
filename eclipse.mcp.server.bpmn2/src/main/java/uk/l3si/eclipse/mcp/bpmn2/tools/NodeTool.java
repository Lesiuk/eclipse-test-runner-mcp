package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.RemoveNodeResult;
import uk.l3si.eclipse.mcp.bpmn2.model.UpdateResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.JavaTypeValidator;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NodeTool implements McpTool {

    private static final Map<String, Set<String>> PROPERTY_VALID_TYPES = Map.of(
            "script", Set.of("scriptTask"),
            "scriptFormat", Set.of("scriptTask"),
            "calledElement", Set.of("callActivity"),
            "taskName", Set.of("task"),
            "displayName", Set.of("task"),
            "icon", Set.of("task"),
            "direction", Set.of("exclusiveGateway"));

    @Override
    public String getName() {
        return "bpmn2_node";
    }

    @Override
    public String getDescription() {
        return "Update or remove an existing node. "
                + "Action 'update' modifies properties valid for the node type (use 'bpmn2_get_process' to see current properties). "
                + "Action 'remove' deletes the node and cleans up all connected sequence flows and diagram elements.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum(
                        "Action to perform", List.of("update", "remove")))
                .property("id", PropertySchema.string("Node ID"))
                .property("name", PropertySchema.string("New display name"))
                .property("script", PropertySchema.string("New script (scriptTask only)"))
                .property("scriptFormat", PropertySchema.string(
                        "New script format (scriptTask only)"))
                .property("calledElement", PropertySchema.string(
                        "New called element (callActivity only)"))
                .property("taskName", PropertySchema.string(
                        "New task name (task only)"))
                .property("displayName", PropertySchema.string(
                        "New display name for task header (task only)"))
                .property("icon", PropertySchema.string(
                        "New icon filename (task only, e.g. dynamo.gif)"))
                .property("direction", PropertySchema.stringEnum(
                        "New direction (exclusiveGateway only)",
                        List.of("diverging", "converging")))
                .required(List.of("file", "action", "id"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "action to perform");
        if (!"update".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException("Invalid action: '" + action + "'. Must be 'update' or 'remove'.");
        }

        if ("update".equals(action)) {
            return executeUpdate(args);
        } else {
            return executeRemove(args);
        }
    }

    private Object executeUpdate(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "node ID");

        String name = args.getString("name");
        String script = args.getString("script");
        String scriptFormat = args.getString("scriptFormat");
        String calledElement = args.getString("calledElement");
        String taskName = args.getString("taskName");
        String displayName = args.getString("displayName");
        String icon = args.getString("icon");
        String direction = args.getString("direction");

        // Check at least one property to update
        if (name == null && script == null && scriptFormat == null
                && calledElement == null && taskName == null
                && displayName == null && icon == null && direction == null) {
            throw new IllegalArgumentException(
                    "No properties to update. Provide at least one of: name, script, "
                            + "scriptFormat, calledElement, taskName, displayName, icon, direction.");
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element node = doc.requireNodeExists(id);
        String nodeType = node.getLocalName();

        List<String> updated = new ArrayList<>();

        // name — applicable to all node types
        if (name != null) {
            node.setAttribute("name", name);
            updateMetaDataValue(node, "elementname", name);
            updated.add("name");
        }

        // script — scriptTask only
        if (script != null) {
            validatePropertyForType("script", nodeType);
            Element scriptEl = findChildElement(node, Bpmn2Document.NS_BPMN2, "script");
            if (scriptEl != null) {
                scriptEl.setTextContent(script);
            } else {
                doc.createTextElement(node, Bpmn2Document.NS_BPMN2, "script", script);
            }
            updated.add("script");
        }

        // scriptFormat — scriptTask only
        if (scriptFormat != null) {
            validatePropertyForType("scriptFormat", nodeType);
            node.setAttribute("scriptFormat", scriptFormat);
            updated.add("scriptFormat");
        }

        // calledElement — callActivity only
        if (calledElement != null) {
            validatePropertyForType("calledElement", nodeType);
            if (calledElement.isBlank()) {
                throw new IllegalArgumentException(
                        "Parameter 'calledElement' must not be blank.");
            }
            node.setAttribute("calledElement", calledElement);
            updated.add("calledElement");
        }

        // taskName — task, userTask only
        if (taskName != null) {
            validatePropertyForType("taskName", nodeType);
            int underscoreIdx = taskName.lastIndexOf('_');
            if (underscoreIdx > 0) {
                String interfaceName = taskName.substring(0, underscoreIdx);
                String methodName = taskName.substring(underscoreIdx + 1);
                String typeError = JavaTypeValidator.validateMethod(interfaceName, methodName);
                if (typeError != null) {
                    throw new IllegalArgumentException(typeError);
                }
            } else if (taskName.contains(".")) {
                String typeError = JavaTypeValidator.validateType(taskName);
                if (typeError != null) {
                    throw new IllegalArgumentException(typeError);
                }
            }
            node.setAttributeNS(Bpmn2Document.NS_TNS, "tns:taskName", taskName);
            updated.add("taskName");
        }

        // displayName — task only (stored as tns:displayName attribute)
        if (displayName != null) {
            validatePropertyForType("displayName", nodeType);
            node.setAttributeNS(Bpmn2Document.NS_TNS, "tns:displayName", displayName);
            updated.add("displayName");
        }

        // icon — task only (stored as tns:icon attribute)
        if (icon != null) {
            validatePropertyForType("icon", nodeType);
            node.setAttributeNS(Bpmn2Document.NS_TNS, "tns:icon", icon);
            updated.add("icon");
        }

        // direction — exclusiveGateway only
        if (direction != null) {
            validatePropertyForType("direction", nodeType);
            if (!"diverging".equals(direction) && !"converging".equals(direction)) {
                throw new IllegalArgumentException(
                        "Invalid direction: '" + direction
                                + "'. Must be 'diverging' or 'converging'.");
            }
            String capitalized = direction.substring(0, 1).toUpperCase()
                    + direction.substring(1);
            node.setAttribute("gatewayDirection", capitalized);
            updated.add("direction");
        }

        doc.save();

        return UpdateResult.builder()
                .id(id)
                .updated(updated)
                .build();
    }

    private Object executeRemove(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "node ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element node = doc.requireNodeExists(id);

        // Check not the only startEvent (plain, without signalEventDefinition)
        if ("startEvent".equals(node.getLocalName()) && !Bpmn2NodeHelper.hasSignalEventDefinition(node)) {
            long plainStartCount = doc.listNodes().stream()
                    .filter(n -> "startEvent".equals(n.getLocalName())
                            && !Bpmn2NodeHelper.hasSignalEventDefinition(n))
                    .count();
            if (plainStartCount <= 1) {
                throw new IllegalArgumentException(
                        "Cannot remove the only startEvent in the process. "
                                + "A process must have at least one startEvent.");
            }
        }

        // Find all connected flows
        List<Element> connectedFlows = new ArrayList<>();
        for (Element flow : doc.listFlows()) {
            String sourceRef = flow.getAttribute("sourceRef");
            String targetRef = flow.getAttribute("targetRef");
            if (id.equals(sourceRef) || id.equals(targetRef)) {
                connectedFlows.add(flow);
            }
        }

        // Remove connected flows and clean up references
        List<String> removedFlows = new ArrayList<>();
        Element diagramPlane = doc.getDiagramPlane();

        for (Element flow : connectedFlows) {
            String flowId = flow.getAttribute("id");
            String sourceRef = flow.getAttribute("sourceRef");
            String targetRef = flow.getAttribute("targetRef");

            // Remove incoming/outgoing refs from the OTHER node
            String otherNodeId = id.equals(sourceRef) ? targetRef : sourceRef;
            Element otherNode = doc.findNodeById(otherNodeId);
            if (otherNode != null) {
                if (id.equals(sourceRef)) {
                    // This flow goes from removed node to other node
                    // Remove the incoming ref from the other node
                    removeFlowRef(otherNode, "incoming", flowId);
                } else {
                    // This flow goes from other node to removed node
                    // Remove the outgoing ref from the other node
                    removeFlowRef(otherNode, "outgoing", flowId);
                }
            }

            // Remove BPMNEdge from diagram
            removeDiagramElement(diagramPlane, flowId);

            // Remove the flow element itself
            doc.removeElement(flow);
            removedFlows.add(flowId);
        }

        // Remove BPMNShape from diagram
        removeDiagramElement(diagramPlane, id);

        // Remove the node element
        doc.removeElement(node);

        doc.save();

        return RemoveNodeResult.builder()
                .id(id)
                .removedFlows(removedFlows)
                .build();
    }

    private void validatePropertyForType(String property, String nodeType) {
        Set<String> validTypes = PROPERTY_VALID_TYPES.get(property);
        if (validTypes != null && !validTypes.contains(nodeType)) {
            throw new IllegalArgumentException(
                    "Cannot set '" + property + "' on a " + nodeType + ". '"
                            + property + "' is only valid for "
                            + String.join(", ", validTypes) + " nodes.");
        }
    }

    /**
     * Updates the metaValue text of an existing metaData element with the given name,
     * if it exists within the node's extensionElements.
     */
    private void updateMetaDataValue(Element node, String metaName, String value) {
        Element extElements = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "extensionElements");
        if (extElements == null) {
            return;
        }
        NodeList children = extElements.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_TNS.equals(el.getNamespaceURI())
                    && "metaData".equals(el.getLocalName())
                    && metaName.equals(el.getAttribute("name"))) {
                Element metaValue = findChildElement(el,
                        Bpmn2Document.NS_TNS, "metaValue");
                if (metaValue != null) {
                    metaValue.setTextContent(value);
                }
                return;
            }
        }
    }

    private static Element findChildElement(Element parent, String ns, String localName) {
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
     * Removes a child element like {@code <bpmn2:incoming>flowId</bpmn2:incoming>}
     * or {@code <bpmn2:outgoing>flowId</bpmn2:outgoing>} from the given node.
     */
    private void removeFlowRef(Element node, String refType, String flowId) {
        NodeList children = node.getChildNodes();
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && refType.equals(el.getLocalName())
                    && flowId.equals(el.getTextContent().trim())) {
                toRemove.add(el);
            }
        }
        for (Element el : toRemove) {
            node.removeChild(el);
        }
    }

    /**
     * Removes the BPMNShape or BPMNEdge from the diagram plane whose
     * bpmnElement attribute matches the given element ID.
     */
    private void removeDiagramElement(Element diagramPlane, String elementId) {
        if (diagramPlane == null) {
            return;
        }
        NodeList children = diagramPlane.getChildNodes();
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && elementId.equals(el.getAttribute("bpmnElement"))) {
                toRemove.add(el);
            }
        }
        for (Element el : toRemove) {
            diagramPlane.removeChild(el);
        }
    }
}
