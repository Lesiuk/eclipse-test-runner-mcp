package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.AddNodeResult;
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
        return "Add, update, or remove a node. "
                + "Action 'add' creates a start event, end event, or extension point (userTask). "
                + "Action 'update' modifies properties valid for the node type (use 'bpmn2_get_process' to see current properties). "
                + "Action 'remove' deletes the node and cleans up all connected sequence flows and diagram elements.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum(
                        "Action to perform", List.of("add", "update", "remove")))
                .property("type", PropertySchema.stringEnum(
                        "Node type to add (required for 'add' action)",
                        List.of("start_event", "end_event", "extension_point")))
                .property("id", PropertySchema.string(
                        "Node ID (required for update/remove, optional for add — auto-generated if omitted)"))
                .property("name", PropertySchema.string(
                        "Display name (required for add, optional for update)"))
                .property("signalRef", PropertySchema.string(
                        "Signal ID for signal start events (signal must exist, start_event only)"))
                .property("groupId", PropertySchema.string(
                        "Group ID for task assignment (e.g. dynamo.review, extension_point only)"))
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
                .required(List.of("file", "action"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "action to perform");
        if (!"add".equals(action) && !"update".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException(
                    "Invalid action: '" + action + "'. Must be 'add', 'update', or 'remove'.");
        }

        if ("add".equals(action)) {
            return executeAdd(args);
        } else if ("update".equals(action)) {
            return executeUpdate(args);
        } else {
            return executeRemove(args);
        }
    }

    private Object executeAdd(Args args) throws Exception {
        String type = args.requireString("type", "node type to add");
        if (!"start_event".equals(type) && !"end_event".equals(type)
                && !"extension_point".equals(type)) {
            throw new IllegalArgumentException(
                    "Invalid type: '" + type
                            + "'. Must be 'start_event', 'end_event', or 'extension_point'.");
        }

        return switch (type) {
            case "start_event" -> executeAddStartEvent(args);
            case "end_event" -> executeAddEndEvent(args);
            case "extension_point" -> executeAddExtensionPoint(args);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private Object executeAddStartEvent(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("StartEvent");
        }

        String signalRef = args.getString("signalRef");

        // Validate signalRef exists if provided
        if (signalRef != null) {
            boolean signalExists = doc.listSignals().stream()
                    .anyMatch(s -> signalRef.equals(s.getAttribute("id")));
            if (!signalExists) {
                throw new IllegalArgumentException(
                        "Signal not found: '" + signalRef
                                + "'. Use 'bpmn2_signal' to create it first.");
            }
        }

        // Only one plain startEvent per process
        if (signalRef == null) {
            boolean hasPlainStart = doc.listNodes().stream()
                    .anyMatch(n -> "startEvent".equals(n.getLocalName())
                            && !Bpmn2NodeHelper.hasSignalEventDefinition(n));
            if (hasPlainStart) {
                throw new IllegalArgumentException(
                        "Process already has a plain startEvent. "
                                + "Only one plain startEvent is allowed. "
                                + "Use 'signalRef' to create a signal start event instead.");
            }
        }

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "startEvent");
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);

        if (signalRef != null) {
            String dataOutputId = doc.generateId("DataOutput");
            String dataOutputAssocId = doc.generateId("DataOutputAssociation");
            String outputSetId = doc.generateId("OutputSet");

            Element dataOutput = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "dataOutput");
            dataOutput.setAttribute("id", dataOutputId);
            dataOutput.setAttribute("name", signalRef + "_Output");

            Element outputAssoc = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
            outputAssoc.setAttribute("id", dataOutputAssocId);
            doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                    dataOutputId);
            doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                    "processCommandFlow");

            Element outputSet = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "outputSet");
            outputSet.setAttribute("id", outputSetId);
            doc.createTextElement(outputSet, Bpmn2Document.NS_BPMN2,
                    "dataOutputRefs", dataOutputId);

            Element signalEventDef = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "signalEventDefinition");
            signalEventDef.setAttribute("id", doc.generateId("SignalEventDefinition"));
            signalEventDef.setAttribute("signalRef", signalRef);
        }

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("startEvent")
                .name(name)
                .build();
    }

    private Object executeAddEndEvent(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("EndEvent");
        }

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "endEvent");
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("endEvent")
                .name(name)
                .build();
    }

    private Object executeAddExtensionPoint(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("UserTask");
        }

        String groupId = args.getString("groupId");

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "userTask");
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);
        Bpmn2NodeHelper.addUserTaskIoSpecification(doc, element, name, groupId);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("userTask")
                .name(name)
                .build();
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
