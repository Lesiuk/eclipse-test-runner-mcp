package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddItemDefinitionResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.Map;

public class ItemDefinitionTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_item_definition";
    }

    @Override
    public String getDescription() {
        return "Add or remove a type definition (itemDefinition). Used for evaluatesToTypeRef on conditional flows.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum("Action to perform", List.of("add", "remove")))
                .property("id", PropertySchema.string("ItemDefinition ID (auto-generated for add, required for remove)"))
                .property("structureRef", PropertySchema.string("Fully qualified Java type (required for add)"))
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
        String structureRef = args.requireString("structureRef", "fully qualified Java type");
        String customId = args.getString("id");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element definitions = doc.getDefinitionsElement();

        // Check if an itemDefinition with matching structureRef already exists
        NodeList children = definitions.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "itemDefinition".equals(el.getLocalName())
                    && structureRef.equals(el.getAttribute("structureRef"))) {
                throw new IllegalArgumentException(
                        "ItemDefinition for type '" + structureRef
                                + "' already exists with ID '" + el.getAttribute("id") + "'.");
            }
        }

        // Validate custom ID uniqueness
        String itemDefId;
        if (customId != null) {
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element el
                        && customId.equals(el.getAttribute("id"))) {
                    throw new IllegalArgumentException(
                            "ID already taken: '" + customId + "'. Choose a different ID.");
                }
            }
            itemDefId = customId;
        } else {
            itemDefId = doc.generateId("ItemDefinition");
        }

        // Create bpmn2:itemDefinition
        Element itemDef = doc.createElement(definitions, Bpmn2Document.NS_BPMN2, "itemDefinition");
        itemDef.setAttribute("id", itemDefId);
        itemDef.setAttribute("isCollection", "false");
        itemDef.setAttribute("structureRef", structureRef);

        doc.save();

        return AddItemDefinitionResult.builder()
                .id(itemDefId)
                .structureRef(structureRef)
                .build();
    }

    private Object executeRemove(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "itemDefinition ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element definitions = doc.getDefinitionsElement();

        // Find the itemDefinition by ID
        Element targetItemDef = null;
        NodeList defChildren = definitions.getChildNodes();
        for (int i = 0; i < defChildren.getLength(); i++) {
            if (defChildren.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "itemDefinition".equals(el.getLocalName())
                    && id.equals(el.getAttribute("id"))) {
                targetItemDef = el;
                break;
            }
        }

        if (targetItemDef == null) {
            throw new IllegalArgumentException("ItemDefinition not found: '" + id + "'");
        }

        // Check no process variable (bpmn2:property) references this itemDefinition
        for (Element prop : doc.listVariables()) {
            if (id.equals(prop.getAttribute("itemSubjectRef"))) {
                throw new IllegalArgumentException(
                        "Cannot remove itemDefinition '" + id
                                + "': it is referenced by variable '"
                                + prop.getAttribute("name") + "'.");
            }
        }

        doc.removeElement(targetItemDef);
        doc.save();

        return Map.of("id", id, "removed", true);
    }
}
