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

public class AddItemDefinitionTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_add_item_definition";
    }

    @Override
    public String getDescription() {
        return "Add a type definition (itemDefinition) to the BPMN2 file. "
                + "Use this when you need a type reference for 'evaluatesToTypeRef' "
                + "on conditional flows, or for any other type that is not tied to "
                + "a process variable. Process variables automatically create their "
                + "own itemDefinitions via 'bpmn2_add_variable'.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("structureRef", PropertySchema.string(
                        "Fully qualified Java type (e.g. com.example.MyClass)"))
                .property("id", PropertySchema.string("ItemDefinition ID (auto-generated if omitted)"))
                .required(List.of("file", "structureRef"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
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
}
