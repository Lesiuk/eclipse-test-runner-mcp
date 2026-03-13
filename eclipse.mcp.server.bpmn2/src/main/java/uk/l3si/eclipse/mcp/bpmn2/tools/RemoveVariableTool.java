package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.RemoveVariableResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class RemoveVariableTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_remove_variable";
    }

    @Override
    public String getDescription() {
        return "Remove a process-level variable and its type definition if no other variable uses it.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Variable name to remove"))
                .required(List.of("file", "name"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "variable name");

        Bpmn2Document doc = Bpmn2Document.parse(file);

        // Find the property element with matching name
        Element targetProperty = null;
        for (Element prop : doc.listVariables()) {
            if (name.equals(prop.getAttribute("name"))) {
                targetProperty = prop;
                break;
            }
        }

        if (targetProperty == null) {
            throw new IllegalArgumentException("Variable not found: '" + name + "'");
        }

        // Get the itemSubjectRef from the property
        String itemSubjectRef = targetProperty.getAttribute("itemSubjectRef");

        // Remove the property element
        doc.removeElement(targetProperty);

        // Check if any remaining variable references the same itemDefinition
        boolean itemDefStillReferenced = false;
        for (Element prop : doc.listVariables()) {
            if (itemSubjectRef.equals(prop.getAttribute("itemSubjectRef"))) {
                itemDefStillReferenced = true;
                break;
            }
        }

        // If no other variable references it, remove the itemDefinition
        if (!itemDefStillReferenced && !itemSubjectRef.isEmpty()) {
            Element definitions = doc.getDefinitionsElement();
            org.w3c.dom.NodeList children = definitions.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element el
                        && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                        && "itemDefinition".equals(el.getLocalName())
                        && itemSubjectRef.equals(el.getAttribute("id"))) {
                    doc.removeElement(el);
                    break;
                }
            }
        }

        doc.save();

        return RemoveVariableResult.builder()
                .name(name)
                .removed(true)
                .build();
    }
}
