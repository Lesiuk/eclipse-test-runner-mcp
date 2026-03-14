package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddTextAnnotationResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TextAnnotationTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_text_annotation";
    }

    @Override
    public String getDescription() {
        return "Add or remove a text annotation (sticky note) on the diagram. "
                + "Use 'attachTo' to visually link the note to a specific node or flow.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum("Action to perform", List.of("add", "remove")))
                .property("id", PropertySchema.string("Annotation ID (auto-generated for add, required for remove)"))
                .property("text", PropertySchema.string("Annotation text (required for add)"))
                .property("attachTo", PropertySchema.string(
                        "ID of the node or flow to associate this annotation with (optional, add only)"))
                .required(List.of("file", "action"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "add or remove");
        if (!"add".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException("Invalid action: '" + action + "'. Must be 'add' or 'remove'.");
        }

        if ("add".equals(action)) {
            return executeAdd(args);
        } else {
            return executeRemove(args);
        }
    }

    private Object executeAdd(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String text = args.requireString("text", "annotation text");
        String attachTo = args.getString("attachTo");
        String customId = args.getString("id");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        // Validate attachTo target exists if provided
        if (attachTo != null) {
            Element node = doc.findNodeById(attachTo);
            Element flow = node == null ? doc.findFlowById(attachTo) : null;
            if (node == null && flow == null) {
                throw new IllegalArgumentException(
                        "Target element not found: '" + attachTo
                                + "'. Use 'bpmn2_get_process' to see available node and flow IDs.");
            }
        }

        // Determine annotation ID
        String annotationId;
        if (customId != null) {
            if (doc.findNodeById(customId) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + customId + "'. Choose a different ID.");
            }
            annotationId = customId;
        } else {
            annotationId = doc.generateId("TextAnnotation");
        }

        // Create bpmn2:textAnnotation
        Element annotation = doc.createElement(process, Bpmn2Document.NS_BPMN2, "textAnnotation");
        annotation.setAttribute("id", annotationId);
        doc.createTextElement(annotation, Bpmn2Document.NS_BPMN2, "text", text);

        // Create bpmn2:association if attachTo is provided
        String associationId = null;
        if (attachTo != null) {
            associationId = doc.generateId("Association");
            Element association = doc.createElement(process, Bpmn2Document.NS_BPMN2, "association");
            association.setAttribute("id", associationId);
            association.setAttribute("sourceRef", annotationId);
            association.setAttribute("targetRef", attachTo);
        }

        doc.save();

        return AddTextAnnotationResult.builder()
                .id(annotationId)
                .associationId(associationId)
                .build();
    }

    private Object executeRemove(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "text annotation ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        // Find the textAnnotation element by ID
        Element targetAnnotation = null;
        NodeList processChildren = process.getChildNodes();
        for (int i = 0; i < processChildren.getLength(); i++) {
            if (processChildren.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "textAnnotation".equals(el.getLocalName())
                    && id.equals(el.getAttribute("id"))) {
                targetAnnotation = el;
                break;
            }
        }

        if (targetAnnotation == null) {
            throw new IllegalArgumentException("Text annotation not found: '" + id + "'");
        }

        // Find and remove any associations that reference this annotation
        List<Element> associationsToRemove = new ArrayList<>();
        for (int i = 0; i < processChildren.getLength(); i++) {
            if (processChildren.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "association".equals(el.getLocalName())) {
                String sourceRef = el.getAttribute("sourceRef");
                String targetRef = el.getAttribute("targetRef");
                if (id.equals(sourceRef) || id.equals(targetRef)) {
                    associationsToRemove.add(el);
                }
            }
        }

        Element diagramPlane = doc.getDiagramPlane();

        // Remove diagram elements for associations
        for (Element assoc : associationsToRemove) {
            String assocId = assoc.getAttribute("id");
            removeDiagramElement(diagramPlane, assocId);
            doc.removeElement(assoc);
        }

        // Remove diagram element for the text annotation
        removeDiagramElement(diagramPlane, id);

        // Remove the text annotation itself
        doc.removeElement(targetAnnotation);

        doc.save();

        return Map.of("id", id, "removed", true);
    }

    /**
     * Removes BPMNShape or BPMNEdge elements from the diagram plane whose
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
