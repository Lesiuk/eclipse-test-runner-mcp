package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.AddFlowResult;
import uk.l3si.eclipse.mcp.bpmn2.model.RemoveFlowResult;
import uk.l3si.eclipse.mcp.bpmn2.model.UpdateResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;

public class FlowTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_flow";
    }

    @Override
    public String getDescription() {
        return "Add, update, or remove a sequence flow. "
                + "Action 'add' connects two nodes (provide source/target; optional condition, name, priority). "
                + "Action 'update' modifies properties of an existing flow. "
                + "Action 'remove' deletes a flow and cleans up incoming/outgoing references.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum(
                        "Action to perform", List.of("add", "update", "remove")))
                .property("id", PropertySchema.string("Flow ID (required for update/remove)"))
                .property("source", PropertySchema.string("Source node ID (required for add)"))
                .property("target", PropertySchema.string("Target node ID (required for add)"))
                .property("name", PropertySchema.string("Label (e.g. YES, NO)"))
                .property("condition", PropertySchema.string("Java condition expression"))
                .property("priority", PropertySchema.string("Flow priority (default: 1)"))
                .property("evaluatesToTypeRef", PropertySchema.string(
                        "ItemDefinition ID for condition return type"))
                .required(List.of("file", "action"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "action to perform");
        if (!"add".equals(action) && !"update".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException("Invalid action: '" + action + "'. Must be 'add', 'update', or 'remove'.");
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
        String file = args.requireString("file", "path to .bpmn2 file");
        String source = args.requireString("source", "source node ID");
        String target = args.requireString("target", "target node ID");
        String name = args.getString("name");
        String condition = args.getString("condition");
        Integer priority = args.getInt("priority");
        String evaluatesToTypeRef = args.getString("evaluatesToTypeRef");

        if (priority != null && priority < 1) {
            throw new IllegalArgumentException(
                    "Invalid priority: " + priority + ". Must be a positive integer.");
        }

        if (evaluatesToTypeRef != null && condition == null) {
            throw new IllegalArgumentException(
                    "Cannot set 'evaluatesToTypeRef' without a 'condition'.");
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);

        if (evaluatesToTypeRef != null) {
            doc.requireItemDefinitionExists(evaluatesToTypeRef);
        }

        // Validate source node exists
        Element sourceNode = doc.requireNodeExists(source);

        // Validate target node exists
        Element targetNode = doc.requireNodeExists(target);

        // Validate source != target
        if (source.equals(target)) {
            throw new IllegalArgumentException(
                    "Cannot create a self-loop: source and target are the same node '" + source + "'");
        }

        // Check no duplicate flow between same source and target
        for (Element flow : doc.listFlows()) {
            String existingSource = flow.getAttribute("sourceRef");
            String existingTarget = flow.getAttribute("targetRef");
            if (source.equals(existingSource) && target.equals(existingTarget)) {
                throw new IllegalArgumentException(
                        "Duplicate flow: a sequence flow from '" + source + "' to '" + target
                                + "' already exists (" + flow.getAttribute("id") + ").");
            }
        }

        // Validate source is not an endEvent
        if ("endEvent".equals(sourceNode.getLocalName())) {
            throw new IllegalArgumentException(
                    "Cannot add outgoing flow from endEvent '" + source + "'");
        }

        // Validate target is not a plain startEvent (signal start events are OK)
        if ("startEvent".equals(targetNode.getLocalName()) && !Bpmn2NodeHelper.hasSignalEventDefinition(targetNode)) {
            throw new IllegalArgumentException(
                    "Cannot add incoming flow to plain startEvent '" + target + "'");
        }

        // Generate flow ID
        String flowId = doc.generateId("SequenceFlow");

        // Create bpmn2:sequenceFlow element
        Element process = doc.getProcessElement();
        Element flowElement = doc.createElement(process, Bpmn2Document.NS_BPMN2, "sequenceFlow");
        flowElement.setAttribute("id", flowId);
        flowElement.setAttribute("sourceRef", source);
        flowElement.setAttribute("targetRef", target);

        // Set name if provided
        if (name != null) {
            flowElement.setAttribute("name", name);
        }

        // Add conditionExpression if condition provided
        if (condition != null) {
            Element condExpr = doc.createElement(flowElement,
                    Bpmn2Document.NS_BPMN2, "conditionExpression");
            condExpr.setAttributeNS(Bpmn2Document.NS_XSI, "xsi:type", "bpmn2:tFormalExpression");
            condExpr.setAttribute("id", doc.generateId("FormalExpression"));
            condExpr.setAttribute("language", "http://www.java.com/java");
            if (evaluatesToTypeRef != null) {
                condExpr.setAttribute("evaluatesToTypeRef", evaluatesToTypeRef);
            }
            condExpr.setTextContent(condition);
        }

        // Set tns:priority attribute (default "1" if not provided)
        String priorityValue = priority != null ? String.valueOf(priority) : "1";
        flowElement.setAttributeNS(Bpmn2Document.NS_TNS, "tns:priority", priorityValue);

        // Add bpmn2:outgoing ref to source node
        doc.createTextElement(sourceNode, Bpmn2Document.NS_BPMN2, "outgoing", flowId);

        // Add bpmn2:incoming ref to target node
        doc.createTextElement(targetNode, Bpmn2Document.NS_BPMN2, "incoming", flowId);

        doc.save();

        return AddFlowResult.builder()
                .id(flowId)
                .source(source)
                .target(target)
                .build();
    }

    private Object executeUpdate(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "flow ID");
        String name = args.getString("name");
        String condition = args.getString("condition");
        Integer priority = args.getInt("priority");
        String evaluatesToTypeRef = args.getString("evaluatesToTypeRef");

        if (priority != null && priority < 1) {
            throw new IllegalArgumentException(
                    "Invalid priority: " + priority + ". Must be a positive integer.");
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);

        if (evaluatesToTypeRef != null) {
            doc.requireItemDefinitionExists(evaluatesToTypeRef);
        }

        Element flow = doc.requireFlowExists(id);

        List<String> updated = new ArrayList<>();

        // Update name
        if (name != null) {
            flow.setAttribute("name", name);
            updated.add("name");
        }

        // Update condition
        if (condition != null) {
            Element existingCondExpr = findChildElement(flow,
                    Bpmn2Document.NS_BPMN2, "conditionExpression");
            if (existingCondExpr != null) {
                existingCondExpr.setTextContent(condition);
            } else {
                Element condExpr = doc.createElement(flow,
                        Bpmn2Document.NS_BPMN2, "conditionExpression");
                condExpr.setAttributeNS(Bpmn2Document.NS_XSI, "xsi:type", "bpmn2:tFormalExpression");
                condExpr.setAttribute("id", doc.generateId("FormalExpression"));
                condExpr.setAttribute("language", "http://www.java.com/java");
                condExpr.setTextContent(condition);
            }
            updated.add("condition");
        }

        // Update priority
        if (priority != null) {
            flow.setAttributeNS(Bpmn2Document.NS_TNS, "tns:priority", String.valueOf(priority));
            updated.add("priority");
        }

        // Update evaluatesToTypeRef on existing conditionExpression
        if (evaluatesToTypeRef != null) {
            Element condExpr = findChildElement(flow,
                    Bpmn2Document.NS_BPMN2, "conditionExpression");
            if (condExpr == null) {
                throw new IllegalArgumentException(
                        "Cannot set 'evaluatesToTypeRef' — flow has no condition expression. "
                                + "Set 'condition' first.");
            }
            condExpr.setAttribute("evaluatesToTypeRef", evaluatesToTypeRef);
            updated.add("evaluatesToTypeRef");
        }

        if (updated.isEmpty()) {
            throw new IllegalArgumentException(
                    "No properties to update. Provide at least one of: name, condition, "
                            + "priority, evaluatesToTypeRef.");
        }

        doc.save();

        return UpdateResult.builder()
                .id(id)
                .updated(updated)
                .build();
    }

    private Object executeRemove(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "flow ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element flow = doc.requireFlowExists(id);

        String sourceRef = flow.getAttribute("sourceRef");
        String targetRef = flow.getAttribute("targetRef");

        // Remove incoming ref from target node
        Element targetNode = doc.findNodeById(targetRef);
        if (targetNode != null) {
            removeFlowRef(targetNode, "incoming", id);
        }

        // Remove outgoing ref from source node
        Element sourceNode = doc.findNodeById(sourceRef);
        if (sourceNode != null) {
            removeFlowRef(sourceNode, "outgoing", id);
        }

        // Remove BPMNEdge from diagram (if exists)
        removeDiagramEdge(doc.getDiagramPlane(), id);

        // Remove the flow element
        doc.removeElement(flow);

        // Build warnings
        List<String> warnings = new ArrayList<>();

        if (sourceNode != null && !"endEvent".equals(sourceNode.getLocalName())) {
            if (!hasFlowRef(sourceNode, "outgoing")) {
                warnings.add("Node '" + sourceRef + "' has no outgoing flows");
            }
        }

        if (targetNode != null && !"startEvent".equals(targetNode.getLocalName())) {
            if (!hasFlowRef(targetNode, "incoming")) {
                warnings.add("Node '" + targetRef + "' has no incoming flows");
            }
        }

        doc.save();

        return RemoveFlowResult.builder()
                .id(id)
                .warnings(warnings)
                .build();
    }

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
     * Checks whether the given node has any child elements of the given ref type
     * (incoming or outgoing).
     */
    private boolean hasFlowRef(Element node, String refType) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && refType.equals(el.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the BPMNEdge from the diagram plane whose bpmnElement attribute
     * matches the given flow ID.
     */
    private void removeDiagramEdge(Element diagramPlane, String flowId) {
        if (diagramPlane == null) {
            return;
        }
        NodeList children = diagramPlane.getChildNodes();
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && flowId.equals(el.getAttribute("bpmnElement"))) {
                toRemove.add(el);
            }
        }
        for (Element el : toRemove) {
            diagramPlane.removeChild(el);
        }
    }
}
