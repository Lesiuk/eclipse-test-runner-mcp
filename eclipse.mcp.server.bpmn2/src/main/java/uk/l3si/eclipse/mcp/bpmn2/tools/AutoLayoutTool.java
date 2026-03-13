package uk.l3si.eclipse.mcp.bpmn2.tools;

import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2LayoutEngine;
import uk.l3si.eclipse.mcp.bpmn2.model.AutoLayoutResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AutoLayoutTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_auto_layout";
    }

    @Override
    public String getDescription() {
        return "Rewrite the BPMNDi diagram section with a clean hierarchical layout. "
                + "Run after adding or modifying nodes and flows.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .required(List.of("file"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");

        Bpmn2Document doc = Bpmn2Document.parse(file);

        // Validate process has at least one node
        if (doc.listNodes().isEmpty()) {
            throw new IllegalArgumentException(
                    "Process has no nodes. Use 'bpmn2_add_node' to add nodes before running auto-layout.");
        }

        Bpmn2LayoutEngine engine = new Bpmn2LayoutEngine();
        int nodesLaid = engine.layout(doc);

        doc.save();

        return AutoLayoutResult.builder()
                .file(file)
                .nodesLaid(nodesLaid)
                .build();
    }
}
