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
import java.util.Map;

public class SignalTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_signal";
    }

    @Override
    public String getDescription() {
        return "Add or remove a signal definition. "
                + "Signals enable event-driven start events — create a signal first, "
                + "then use 'bpmn2_node' with type=start_event and signalRef to create a signal start event.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum("Action to perform", List.of("add", "remove")))
                .property("name", PropertySchema.string("Signal name (required for add)"))
                .property("id", PropertySchema.string("Signal ID (auto-generated for add, required for remove)"))
                .required(List.of("file", "action"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String action = args.requireString("action", "add or remove");
        if (!"add".equals(action) && !"remove".equals(action)) {
            throw new IllegalArgumentException("Invalid action: '" + action + "'. Must be 'add' or 'remove'.");
        }

        if ("add".equals(action)) {
            return executeAdd(args);
        } else {
            return executeRemove(args);
        }
    }

    private Object executeAdd(Args args) throws Exception {
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

    private Object executeRemove(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String id = args.requireString("id", "signal ID");

        Bpmn2Document doc = Bpmn2Document.parse(file);

        // Find the signal by ID
        Element targetSignal = null;
        for (Element signal : doc.listSignals()) {
            if (id.equals(signal.getAttribute("id"))) {
                targetSignal = signal;
                break;
            }
        }

        if (targetSignal == null) {
            throw new IllegalArgumentException("Signal not found: '" + id + "'");
        }

        // Check no startEvent references this signal via signalEventDefinition
        Element process = doc.getProcessElement();
        NodeList processChildren = process.getChildNodes();
        for (int i = 0; i < processChildren.getLength(); i++) {
            if (processChildren.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "startEvent".equals(el.getLocalName())) {
                // Check child signalEventDefinition elements
                NodeList eventChildren = el.getChildNodes();
                for (int j = 0; j < eventChildren.getLength(); j++) {
                    if (eventChildren.item(j) instanceof Element sed
                            && Bpmn2Document.NS_BPMN2.equals(sed.getNamespaceURI())
                            && "signalEventDefinition".equals(sed.getLocalName())
                            && id.equals(sed.getAttribute("signalRef"))) {
                        throw new IllegalArgumentException(
                                "Cannot remove signal '" + id
                                        + "': it is referenced by startEvent '"
                                        + el.getAttribute("id") + "'.");
                    }
                }
            }
        }

        doc.removeElement(targetSignal);
        doc.save();

        return Map.of("id", id, "removed", true);
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
