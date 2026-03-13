package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.RemoveNodeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;

public class RemoveNodeTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_remove_node";
    }

    @Override
    public String getDescription() {
        return "Remove a node and automatically clean up all connected sequence flows "
                + "and diagram elements.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("id", PropertySchema.string("Node ID to remove"))
                .required(List.of("file", "id"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "node ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element node = doc.requireNodeExists(id);

        // Check not the only startEvent (plain, without signalEventDefinition)
        if ("startEvent".equals(node.getLocalName()) && !hasSignalEventDefinition(node)) {
            long plainStartCount = doc.listNodes().stream()
                    .filter(n -> "startEvent".equals(n.getLocalName())
                            && !hasSignalEventDefinition(n))
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

    private boolean hasSignalEventDefinition(Element startEvent) {
        NodeList children = startEvent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "signalEventDefinition".equals(el.getLocalName())) {
                return true;
            }
        }
        return false;
    }
}
