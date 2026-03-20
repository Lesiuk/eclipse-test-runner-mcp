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

public class AddScriptTaskTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_script_task";
    }

    @Override
    public String getDescription() {
        return "Add a script task that executes inline Java code. "
                + "The 'script' parameter contains the Java code to run. "
                + "Script format defaults to Java. "
                + "After adding, use 'bpmn2_flow' to connect it to other nodes.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Display name"))
                .property("script", PropertySchema.string("Java code to execute"))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .property("scriptFormat", PropertySchema.string(
                        "Script language URI (default: http://www.java.com/java)"))
                .required(List.of("file", "name", "script"))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "display name");
        String script = args.requireString("script", "Java code");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId("ScriptTask");
        }

        String scriptFormat = args.getString("scriptFormat");
        if (scriptFormat == null || scriptFormat.isBlank()) {
            scriptFormat = "http://www.java.com/java";
        }

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "scriptTask");
        element.setAttribute("id", id);
        element.setAttribute("name", name);
        element.setAttribute("scriptFormat", scriptFormat);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);
        doc.createTextElement(element, Bpmn2Document.NS_BPMN2, "script", script);

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("scriptTask")
                .name(name)
                .build();
    }
}
