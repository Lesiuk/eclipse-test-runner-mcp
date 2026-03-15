package uk.l3si.eclipse.mcp.bpmn2.tools;

import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.CreateProcessResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.regex.Pattern;

public class CreateProcessTool implements McpTool {

    private static final Pattern VALID_PROCESS_ID = Pattern.compile("[a-zA-Z0-9._]+");
    private static final Pattern VALID_PACKAGE_NAME = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)*");

    @Override
    public String getName() {
        return "bpmn2_create_process";
    }

    @Override
    public String getDescription() {
        return "Create a new .bpmn2 file with boilerplate XML including namespace declarations, "
                + "common type definitions, and an empty process. "
                + "After creating, use 'bpmn2_node' to add start/end events, then add tasks and connect with 'bpmn2_flow'.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path for new .bpmn2 file"))
                .property("processId", PropertySchema.string("Process identifier (e.g. 'com.example.my_flow')"))
                .property("processName", PropertySchema.string("Human-readable process name"))
                .property("packageName", PropertySchema.string("Package name (e.g. 'com.example')"))
                .required(List.of("file", "processId", "processName", "packageName"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "absolute path for new file");
        String processId = args.requireString("processId", "process identifier");
        String processName = args.requireString("processName", "human-readable name");
        String packageName = args.requireString("packageName", "package name");

        if (!file.endsWith(".bpmn2")) {
            throw new IllegalArgumentException(
                    "File must have a .bpmn2 extension: '" + file + "'");
        }

        if (!VALID_PROCESS_ID.matcher(processId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid processId: '" + processId
                            + "'. Must contain only letters, digits, dots, and underscores.");
        }

        if (!VALID_PACKAGE_NAME.matcher(packageName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid packageName: '" + packageName
                            + "'. Must be a valid Java package name (e.g. com.example).");
        }

        Bpmn2Document.create(file, processId, processName, packageName);

        return CreateProcessResult.builder()
                .file(file)
                .processId(processId)
                .build();
    }
}
