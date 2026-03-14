package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddTextAnnotationResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AddTextAnnotationTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_add_text_annotation";
    }

    @Override
    public String getDescription() {
        return "Add a text annotation (sticky note) to the diagram. "
                + "Optionally attach it to a node or flow with an association line. "
                + "Use this to add comments, explanations, or documentation notes "
                + "that appear visually on the BPMN diagram.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("text", PropertySchema.string("Annotation text"))
                .property("attachTo", PropertySchema.string(
                        "ID of the node or flow to associate this annotation with (optional)"))
                .property("id", PropertySchema.string("Annotation ID (auto-generated if omitted)"))
                .required(List.of("file", "text"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
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
}
