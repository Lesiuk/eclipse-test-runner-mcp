package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddSignalResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AddSignalTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_add_signal";
    }

    @Override
    public String getDescription() {
        return "Add a signal definition to the BPMN2 file.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Signal name (e.g. com.example:beforeInput)"))
                .property("id", PropertySchema.string("Signal ID (auto-generated if omitted)"))
                .required(List.of("file", "name"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "signal name");
        String customId = args.getString("id");

        Bpmn2Document doc = Bpmn2Document.parse(file);

        // Check no existing signal with same name
        for (Element signal : doc.listSignals()) {
            if (name.equals(signal.getAttribute("name"))) {
                throw new IllegalArgumentException(
                        "Signal with name '" + name + "' already exists. Use a different name.");
            }
        }

        // Determine signal ID
        String signalId;
        if (customId != null && !customId.isBlank()) {
            // Check if the custom id is already taken by any element
            if (isIdTaken(doc, customId)) {
                throw new IllegalArgumentException(
                        "ID '" + customId + "' is already in use. Choose a different ID.");
            }
            signalId = customId;
        } else {
            signalId = doc.generateId("Signal");
        }

        // Create bpmn2:signal element in definitions
        Element signal = doc.createElement(doc.getDefinitionsElement(), Bpmn2Document.NS_BPMN2, "signal");
        signal.setAttribute("id", signalId);
        signal.setAttribute("name", name);

        doc.save();

        return AddSignalResult.builder()
                .id(signalId)
                .name(name)
                .build();
    }

    /**
     * Checks whether any element in the document already uses the given ID.
     */
    private boolean isIdTaken(Bpmn2Document doc, String id) {
        Element definitions = doc.getDefinitionsElement();
        NodeList allElements = definitions.getOwnerDocument().getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element el
                    && id.equals(el.getAttribute("id"))) {
                return true;
            }
        }
        return false;
    }
}
