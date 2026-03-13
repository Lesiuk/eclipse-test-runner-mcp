package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddNodeResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AddNodeTool implements McpTool {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "startEvent", "endEvent", "task", "scriptTask",
            "userTask", "callActivity", "exclusiveGateway");

    private static final Set<String> TASK_TYPES = Set.of(
            "task", "scriptTask", "userTask", "callActivity");

    private static final Map<String, String> TYPE_TO_ID_PREFIX = Map.of(
            "startEvent", "StartEvent",
            "endEvent", "EndEvent",
            "task", "Task",
            "scriptTask", "ScriptTask",
            "userTask", "UserTask",
            "callActivity", "CallActivity",
            "exclusiveGateway", "ExclusiveGateway");

    @Override
    public String getName() {
        return "bpmn2_add_node";
    }

    @Override
    public String getDescription() {
        return "Add a flow node to the process. Supports startEvent, endEvent, task, "
                + "scriptTask, userTask, callActivity, and exclusiveGateway.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("type", PropertySchema.stringEnum("Node type",
                        List.of("startEvent", "endEvent", "task", "scriptTask",
                                "userTask", "callActivity", "exclusiveGateway")))
                .property("name", PropertySchema.string("Display name"))
                .property("id", PropertySchema.string("Node ID (auto-generated if omitted)"))
                .property("taskName", PropertySchema.string(
                        "Fully qualified service interface + method (required for task)"))
                .property("displayName", PropertySchema.string("Display name for tasks"))
                .property("icon", PropertySchema.string("Icon name for tasks"))
                .property("script", PropertySchema.string("Script content (required for scriptTask)"))
                .property("scriptFormat", PropertySchema.string(
                        "Script language (default: http://www.java.com/java)"))
                .property("calledElement", PropertySchema.string(
                        "Called element (required for callActivity)"))
                .property("direction", PropertySchema.stringEnum(
                        "Gateway direction (required for exclusiveGateway)",
                        List.of("diverging", "converging")))
                .property("signalRef", PropertySchema.string("Signal ID for signal start events"))
                .property("groupId", PropertySchema.string("Group ID for userTask"))
                .required(List.of("file", "type", "name"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String type = args.requireString("type", "node type");
        String name = args.requireString("name", "display name");

        if (!ALLOWED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Invalid type: '" + type + "'. Must be one of: " + ALLOWED_TYPES);
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        // Resolve ID
        String id = args.getString("id");
        if (id != null) {
            if (doc.findNodeById(id) != null) {
                throw new IllegalArgumentException(
                        "ID already taken: '" + id + "'. Choose a different ID.");
            }
        } else {
            id = doc.generateId(TYPE_TO_ID_PREFIX.get(type));
        }

        // Optional parameters
        String taskName = args.getString("taskName");
        String displayName = args.getString("displayName");
        String icon = args.getString("icon");
        String script = args.getString("script");
        String scriptFormat = args.getString("scriptFormat");
        String calledElement = args.getString("calledElement");
        String direction = args.getString("direction");
        String signalRef = args.getString("signalRef");
        String groupId = args.getString("groupId");

        // Type-specific validation
        validateTypeSpecificParams(type, taskName, script, calledElement, direction, signalRef, doc);

        // Create the element
        Element element = doc.createElement(process, Bpmn2Document.NS_BPMN2, type);
        element.setAttribute("id", id);
        element.setAttribute("name", name);

        switch (type) {
            case "task" -> buildTask(doc, element, name, taskName, displayName, icon);
            case "scriptTask" -> buildScriptTask(doc, element, name, script, scriptFormat);
            case "userTask" -> buildUserTask(doc, element, name, groupId);
            case "callActivity" -> buildCallActivity(doc, element, name, calledElement);
            case "exclusiveGateway" -> buildExclusiveGateway(element, direction);
            case "startEvent" -> buildStartEvent(doc, element, name, signalRef);
            case "endEvent" -> buildEndEvent(doc, element, name);
        }

        doc.save();

        return AddNodeResult.builder()
                .id(id)
                .type(type)
                .name(name)
                .build();
    }

    private void validateTypeSpecificParams(String type, String taskName, String script,
                                            String calledElement, String direction,
                                            String signalRef, Bpmn2Document doc) {
        switch (type) {
            case "task" -> {
                if (taskName == null || taskName.isBlank()) {
                    throw new IllegalArgumentException(
                            "Parameter 'taskName' is required for type 'task'");
                }
            }
            case "scriptTask" -> {
                if (script == null || script.isBlank()) {
                    throw new IllegalArgumentException(
                            "Parameter 'script' is required for type 'scriptTask'");
                }
            }
            case "callActivity" -> {
                if (calledElement == null || calledElement.isBlank()) {
                    throw new IllegalArgumentException(
                            "Parameter 'calledElement' is required for type 'callActivity'");
                }
            }
            case "exclusiveGateway" -> {
                if (direction == null || direction.isBlank()) {
                    throw new IllegalArgumentException(
                            "Parameter 'direction' is required for type 'exclusiveGateway'");
                }
                if (!"diverging".equals(direction) && !"converging".equals(direction)) {
                    throw new IllegalArgumentException(
                            "Invalid direction: '" + direction
                                    + "'. Must be 'diverging' or 'converging'.");
                }
            }
        }

        if (signalRef != null) {
            boolean signalExists = doc.listSignals().stream()
                    .anyMatch(s -> signalRef.equals(s.getAttribute("id")));
            if (!signalExists) {
                throw new IllegalArgumentException(
                        "Signal not found: '" + signalRef
                                + "'. Use 'bpmn2_add_signal' to create it first.");
            }
        }

        if ("startEvent".equals(type) && signalRef == null) {
            // Only one plain startEvent per process
            boolean hasPlainStart = doc.listNodes().stream()
                    .anyMatch(n -> "startEvent".equals(n.getLocalName())
                            && !hasSignalEventDefinition(n));
            if (hasPlainStart) {
                throw new IllegalArgumentException(
                        "Process already has a plain startEvent. "
                                + "Only one plain startEvent is allowed. "
                                + "Use 'signalRef' to create a signal start event instead.");
            }
        }
    }

    private boolean hasSignalEventDefinition(Element startEvent) {
        org.w3c.dom.NodeList children = startEvent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "signalEventDefinition".equals(el.getLocalName())) {
                return true;
            }
        }
        return false;
    }

    // ---- Task types ----

    private void buildTask(Bpmn2Document doc, Element element, String name,
                           String taskName, String displayName, String icon) {
        element.setAttributeNS(Bpmn2Document.NS_TNS, "tns:taskName", taskName);

        Element extElements = addExtensionElements(doc, element, name);
        if (displayName != null) {
            addMetaData(doc, extElements, "displayName", displayName);
        }
        if (icon != null) {
            addMetaData(doc, extElements, "icon", icon);
        }

        addIoSpecification(doc, element);
    }

    private void buildScriptTask(Bpmn2Document doc, Element element, String name,
                                 String script, String scriptFormat) {
        if (scriptFormat == null || scriptFormat.isBlank()) {
            scriptFormat = "http://www.java.com/java";
        }
        element.setAttribute("scriptFormat", scriptFormat);

        doc.createTextElement(element, Bpmn2Document.NS_BPMN2, "script", script);

        addExtensionElements(doc, element, name);
        addIoSpecification(doc, element);
    }

    private void buildUserTask(Bpmn2Document doc, Element element, String name, String groupId) {
        if (groupId != null) {
            element.setAttribute("groupId", groupId);
        }

        addExtensionElements(doc, element, name);
        addIoSpecification(doc, element);
    }

    private void buildCallActivity(Bpmn2Document doc, Element element, String name,
                                   String calledElement) {
        element.setAttribute("calledElement", calledElement);

        addExtensionElements(doc, element, name);
        addIoSpecification(doc, element);
    }

    // ---- Non-task types ----

    private void buildExclusiveGateway(Element element, String direction) {
        String capitalized = direction.substring(0, 1).toUpperCase() + direction.substring(1);
        element.setAttribute("gatewayDirection", capitalized);
    }

    private void buildStartEvent(Bpmn2Document doc, Element element, String name,
                                 String signalRef) {
        addExtensionElements(doc, element, name);

        if (signalRef != null) {
            Element signalEventDef = doc.createElement(element,
                    Bpmn2Document.NS_BPMN2, "signalEventDefinition");
            signalEventDef.setAttribute("id", doc.generateId("SignalEventDefinition"));
            signalEventDef.setAttribute("signalRef", signalRef);
        }
    }

    private void buildEndEvent(Bpmn2Document doc, Element element, String name) {
        addExtensionElements(doc, element, name);
    }

    // ---- Helpers ----

    /**
     * Adds extensionElements with a metaData element for "elementname".
     */
    private Element addExtensionElements(Bpmn2Document doc, Element parent, String name) {
        Element extElements = doc.createElement(parent,
                Bpmn2Document.NS_BPMN2, "extensionElements");
        addMetaData(doc, extElements, "elementname", name);
        return extElements;
    }

    /**
     * Adds a tns:metaData element with a tns:metaValue child.
     */
    private void addMetaData(Bpmn2Document doc, Element parent,
                             String metaName, String metaValue) {
        Element metaData = doc.createElement(parent, Bpmn2Document.NS_TNS, "metaData");
        metaData.setAttribute("name", metaName);
        doc.createTextElement(metaData, Bpmn2Document.NS_TNS, "metaValue", metaValue);
    }

    /**
     * Adds the ioSpecification boilerplate with dataInput/dataOutput associations.
     */
    private void addIoSpecification(Bpmn2Document doc, Element taskElement) {
        String ioSpecId = doc.generateId("InputOutputSpecification");
        String dataInputId = doc.generateId("DataInput");
        String dataOutputId = doc.generateId("DataOutput");
        String inputSetId = doc.generateId("InputSet");
        String outputSetId = doc.generateId("OutputSet");
        String dataInputAssocId = doc.generateId("DataInputAssociation");
        String dataOutputAssocId = doc.generateId("DataOutputAssociation");

        // ioSpecification
        Element ioSpec = doc.createElement(taskElement,
                Bpmn2Document.NS_BPMN2, "ioSpecification");
        ioSpec.setAttribute("id", ioSpecId);

        Element dataInput = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataInput");
        dataInput.setAttribute("id", dataInputId);
        dataInput.setAttribute("name", "taskCommandFlow");

        Element dataOutput = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataOutput");
        dataOutput.setAttribute("id", dataOutputId);
        dataOutput.setAttribute("name", "taskCommandFlow");

        Element inputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "inputSet");
        inputSet.setAttribute("id", inputSetId);
        doc.createTextElement(inputSet, Bpmn2Document.NS_BPMN2, "dataInputRefs", dataInputId);

        Element outputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "outputSet");
        outputSet.setAttribute("id", outputSetId);
        doc.createTextElement(outputSet, Bpmn2Document.NS_BPMN2, "dataOutputRefs", dataOutputId);

        // dataInputAssociation
        Element inputAssoc = doc.createElement(taskElement,
                Bpmn2Document.NS_BPMN2, "dataInputAssociation");
        inputAssoc.setAttribute("id", dataInputAssocId);
        doc.createTextElement(inputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                "processCommandFlow");
        doc.createTextElement(inputAssoc, Bpmn2Document.NS_BPMN2, "targetRef", dataInputId);

        // dataOutputAssociation
        Element outputAssoc = doc.createElement(taskElement,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        outputAssoc.setAttribute("id", dataOutputAssocId);
        doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef", dataOutputId);
        doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                "processCommandFlow");
    }
}
