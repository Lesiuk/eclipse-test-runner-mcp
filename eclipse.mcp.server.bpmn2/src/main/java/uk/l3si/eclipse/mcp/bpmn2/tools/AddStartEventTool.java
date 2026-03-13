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

public class AddStartEventTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_add_start_event";
    }

    @Override
    public String getDescription() {
        return "Add a start event to the process. "
                + "Only one plain start event is allowed per process. "
                + "To add additional start events, provide a 'signalRef' "
                + "(the signal must already exist — use 'bpmn2_add_signal' first). "
                + "After adding, use 'bpmn2_add_flow' to connect it to the first task.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("name", PropertySchema.string("Display name"))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .property("signalRef", PropertySchema.string(
                        "Signal ID for signal start events (signal must exist)"))
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
            id = doc.generateId("StartEvent");
        }

        String signalRef = args.getString("signalRef");

        // Validate signalRef exists if provided
        if (signalRef != null) {
            boolean signalExists = doc.listSignals().stream()
                    .anyMatch(s -> signalRef.equals(s.getAttribute("id")));
            if (!signalExists) {
                throw new IllegalArgumentException(
                        "Signal not found: '" + signalRef
                                + "'. Use 'bpmn2_add_signal' to create it first.");
            }
        }

        // Only one plain startEvent per process
        if (signalRef == null) {
            boolean hasPlainStart = doc.listNodes().stream()
                    .anyMatch(n -> "startEvent".equals(n.getLocalName())
                            && !Bpmn2NodeHelper.hasSignalEventDefinition(n));
            if (hasPlainStart) {
                throw new IllegalArgumentException(
                        "Process already has a plain startEvent. "
                                + "Only one plain startEvent is allowed. "
                                + "Use 'signalRef' to create a signal start event instead.");
            }
        }

        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, "startEvent");
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        Bpmn2NodeHelper.addExtensionElements(doc, element, name);

        if (signalRef != null) {
            String dataOutputId = doc.generateId("DataOutput");
            String dataOutputAssocId = doc.generateId("DataOutputAssociation");
            String outputSetId = doc.generateId("OutputSet");

            Element dataOutput = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "dataOutput");
            dataOutput.setAttribute("id", dataOutputId);
            dataOutput.setAttribute("name", signalRef + "_Output");

            Element outputAssoc = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
            outputAssoc.setAttribute("id", dataOutputAssocId);
            doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                    dataOutputId);
            doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                    "processCommandFlow");

            Element outputSet = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "outputSet");
            outputSet.setAttribute("id", outputSetId);
            doc.createTextElement(outputSet, Bpmn2Document.NS_BPMN2,
                    "dataOutputRefs", dataOutputId);

            Element signalEventDef = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "signalEventDefinition");
            signalEventDef.setAttribute("id", doc.generateId("SignalEventDefinition"));
            signalEventDef.setAttribute("signalRef", signalRef);
        }

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type("startEvent")
                .name(name)
                .build();
    }
}
