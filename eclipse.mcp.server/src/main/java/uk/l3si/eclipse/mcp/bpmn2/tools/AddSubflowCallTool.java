package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.AddNodeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AddSubflowCallTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_subflow_call";
    }

    @Override
    public String getDescription() {
        return "Add a call activity that invokes another BPMN2 subprocess. "
                + "The 'calledElement' is the subprocess process ID "
                + "(e.g. com.example.sub_flow). "
                + "waitForCompletion and independent are always true. "
                + "After adding, use 'bpmn2_flow' to connect it to other nodes.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Display name"))
                .property("calledElement", PropertySchema.string(
                        "Process ID of the subprocess to call (e.g. com.example.sub_flow)"))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .required(List.of("file", "name", "calledElement"))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");
        String calledElement = args.requireString("calledElement", "subprocess process ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("CallActivity");
        }

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "callActivity");
        element.setAttribute("id", id);
        element.setAttribute("name", name);
        element.setAttributeNS(Bpmn2Document.NS_TNS, "tns:waitForCompletion", "true");
        element.setAttributeNS(Bpmn2Document.NS_TNS, "tns:independent", "true");
        element.setAttribute("calledElement", calledElement);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);
        Bpmn2NodeHelper.addCallActivityIoSpecification(doc, element);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("callActivity")
                .name(name)
                .build();
    }
}
