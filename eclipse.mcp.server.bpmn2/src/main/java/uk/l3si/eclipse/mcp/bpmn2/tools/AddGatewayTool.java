package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddNodeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AddGatewayTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_gateway";
    }

    @Override
    public String getDescription() {
        return "Add an exclusive gateway for branching or merging flow paths. "
                + "For diverging (decision): add the gateway, then connect each branch "
                + "using 'bpmn2_flow' with a Java 'condition' and 'name' label "
                + "(e.g. YES/NO). "
                + "For converging (merge): add the gateway and connect incoming flows to it.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Display name"))
                .property("direction", PropertySchema.stringEnum(
                        "Gateway direction: diverging for decisions, converging for merges",
                        List.of("diverging", "converging")))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .required(List.of("file", "name", "direction"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");
        String direction = args.requireString("direction", "gateway direction");

        if (!"diverging".equals(direction) && !"converging".equals(direction)) {
            throw new IllegalArgumentException(
                    "Invalid direction: '" + direction
                            + "'. Must be 'diverging' or 'converging'.");
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("ExclusiveGateway");
        }

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "exclusiveGateway");
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        String capitalized = direction.substring(0, 1).toUpperCase() + direction.substring(1);
        element.setAttribute("gatewayDirection", capitalized);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("exclusiveGateway")
                .name(name)
                .build();
    }
}
