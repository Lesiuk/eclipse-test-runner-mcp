package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddVariableResult;
import uk.l3si.eclipse.mcp.bpmn2.model.RemoveVariableResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.regex.Pattern;

public class VariableTool implements McpTool {

    private static final Pattern VALID_JAVA_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    @Override
    public String getName() {
        return "bpmn2_variable";
    }

    @Override
    public String getDescription() {
        return "Add or remove a process-level variable. "
                + "Variables are used in condition expressions on flows and as data passed between tasks. "
                + "Type definitions (itemDefinitions) are auto-managed.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum("Action to perform", List.of("add", "remove")))
                .property("name", PropertySchema.string("Variable name"))
                .property("type", PropertySchema.string("Fully qualified Java type (required for add, ignored for remove)"))
                .required(List.of("file", "action", "name"))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
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
        String name = args.requireString("name", "variable name");
        String type = args.requireString("type", "fully qualified Java type");

        // Validate variable name is a valid Java identifier
        if (!VALID_JAVA_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid variable name: '" + name
                            + "'. Must be a valid Java identifier (letters, digits, underscores, starts with letter or underscore).");
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);

        // Check variable name doesn't already exist
        for (Element prop : doc.listVariables()) {
            if (name.equals(prop.getAttribute("name"))) {
                throw new IllegalArgumentException(
                        "Variable '" + name + "' already exists in the process. Use a different name.");
            }
        }

        // Check if an itemDefinition with matching structureRef already exists
        String itemDefinitionId = null;
        Element definitions = doc.getDefinitionsElement();
        for (Element el : directChildrenByLocalName(definitions, Bpmn2Document.NS_BPMN2, "itemDefinition")) {
            if (type.equals(el.getAttribute("structureRef"))) {
                itemDefinitionId = el.getAttribute("id");
                break;
            }
        }

        // If not found, create a new itemDefinition
        if (itemDefinitionId == null) {
            itemDefinitionId = doc.generateId("ItemDefinition");
            Element itemDef = doc.createElement(definitions, Bpmn2Document.NS_BPMN2, "itemDefinition");
            itemDef.setAttribute("id", itemDefinitionId);
            itemDef.setAttribute("isCollection", "false");
            itemDef.setAttribute("structureRef", type);
        }

        // Create bpmn2:property in the process element
        Element property = doc.createElement(doc.getProcessElement(), Bpmn2Document.NS_BPMN2, "property");
        property.setAttribute("id", name);
        property.setAttribute("name", name);
        property.setAttribute("itemSubjectRef", itemDefinitionId);

        doc.save();

        return AddVariableResult.builder()
                .name(name)
                .type(type)
                .itemDefinitionId(itemDefinitionId)
                .build();
    }

    private Object executeRemove(Args args) throws Exception {
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

    private static List<Element> directChildrenByLocalName(Element parent, String ns, String localName) {
        List<Element> result = new java.util.ArrayList<>();
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                result.add(el);
            }
        }
        return result;
    }
}
