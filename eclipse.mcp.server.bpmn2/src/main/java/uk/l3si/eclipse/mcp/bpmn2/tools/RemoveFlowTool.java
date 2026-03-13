package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.RemoveFlowResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;

public class RemoveFlowTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_remove_flow";
    }

    @Override
    public String getDescription() {
        return "Remove a sequence flow and clean up incoming/outgoing references on connected nodes.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("id", PropertySchema.string("Flow ID to remove"))
                .required(List.of("file", "id"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
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
