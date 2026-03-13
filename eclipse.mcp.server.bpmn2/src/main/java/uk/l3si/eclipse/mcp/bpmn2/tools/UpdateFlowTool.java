package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.UpdateResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;

public class UpdateFlowTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_update_flow";
    }

    @Override
    public String getDescription() {
        return "Modify properties of an existing sequence flow.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("id", PropertySchema.string("Flow ID to update"))
                .property("name", PropertySchema.string("New label"))
                .property("condition", PropertySchema.string("New condition expression"))
                .property("priority", PropertySchema.string("New priority"))
                .property("evaluatesToTypeRef", PropertySchema.string(
                        "ItemDefinition ID for condition return type"))
                .required(List.of("file", "id"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
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
}
