package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.AddNodeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.JavaTypeValidator;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.regex.Pattern;

public class AddServiceTaskTool implements McpTool {

    static final Pattern VALID_TASK_NAME = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)+(_[a-zA-Z][a-zA-Z0-9]*)?");

    @Override
    public String getName() {
        return "bpmn2_service_task";
    }

    @Override
    public String getDescription() {
        return "Add a service task that calls a Java service method. "
                + "The 'taskName' is the fully-qualified interface + method "
                + "(e.g. com.example.IService_doSomething). "
                + "IO specification and extension elements are auto-generated. "
                + "After adding, use 'bpmn2_flow' to connect it to other nodes.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Display name"))
                .property("taskName", PropertySchema.string(
                        "Fully qualified service interface + method (e.g. com.example.IService_doSomething)"))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .property("displayName", PropertySchema.string("Display name shown in the task header"))
                .property("icon", PropertySchema.string("Icon filename (e.g. dynamo.gif)"))
                .required(List.of("file", "name", "taskName"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");
        String taskName = args.requireString("taskName", "service interface + method");

        if (!VALID_TASK_NAME.matcher(taskName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid taskName: '" + taskName
                            + "'. Expected format: fully.qualified.ClassName or "
                            + "fully.qualified.Interface_methodName "
                            + "(e.g. com.example.IService_doSomething).");
        }

        // Validate interface/method exists in workspace (skipped outside Eclipse)
        int underscoreIdx = taskName.lastIndexOf('_');
        if (underscoreIdx > 0) {
            String interfaceName = taskName.substring(0, underscoreIdx);
            String methodName = taskName.substring(underscoreIdx + 1);
            String error = JavaTypeValidator.validateMethod(interfaceName, methodName);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
        } else {
            String error = JavaTypeValidator.validateType(taskName);
            if (error != null) {
                throw new IllegalArgumentException(error);
            }
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
            id = doc.generateId("Task");
        }

        String displayName = args.getString("displayName");
        String icon = args.getString("icon");

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "task");
        element.setAttribute("id", id);
        element.setAttribute("name", name);
        element.setAttributeNS(Bpmn2Document.NS_TNS, "tns:taskName", taskName);
        if (displayName != null) {
            element.setAttributeNS(Bpmn2Document.NS_TNS, "tns:displayName", displayName);
        }
        if (icon != null) {
            element.setAttributeNS(Bpmn2Document.NS_TNS, "tns:icon", icon);
        }

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);
        Bpmn2NodeHelper.addTaskIoSpecification(doc, element);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("task")
                .name(name)
                .build();
    }
}
