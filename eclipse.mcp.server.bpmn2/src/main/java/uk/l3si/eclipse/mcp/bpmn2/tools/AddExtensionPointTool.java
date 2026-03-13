package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.AddNodeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AddExtensionPointTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_add_extension_point";
    }

    @Override
    public String getDescription() {
        return "Add a web extension point (jBPM human task / userTask). "
                + "This creates a userTask with TypeHumanTask=WebExtensionPoint, "
                + "Skippable=true, Locale=en-UK, and Priority=1 pre-configured. "
                + "After adding, use 'bpmn2_add_flow' to connect it to other nodes.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Display name"))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .property("groupId", PropertySchema.string(
                        "Group ID for task assignment (e.g. dynamo.review)"))
                .required(List.of("file", "name"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("UserTask");
        }

        String groupId = args.getString("groupId");

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "userTask");
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);
        Bpmn2NodeHelper.addUserTaskIoSpecification(doc, element, name, groupId);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("userTask")
                .name(name)
                .build();
    }
}
