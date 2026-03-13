package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2NodeHelper;
import uk.l3si.eclipse.mcp.bpmn2.model.AddFlowResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class AddFlowTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_add_flow";
    }

    @Override
    public String getDescription() {
        return "Connect two nodes with a sequence flow. "
                + "For conditional branches from a diverging gateway, provide a Java "
                + "'condition' (e.g. 'return amount > 100;') and a 'name' label (e.g. YES/NO). "
                + "Priority defaults to 1.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("source", PropertySchema.string("Source node ID"))
                .property("target", PropertySchema.string("Target node ID"))
                .property("name", PropertySchema.string("Label (e.g. YES, NO)"))
                .property("condition", PropertySchema.string("Java condition expression"))
                .property("priority", PropertySchema.string("Flow priority (default: 1)"))
                .property("evaluatesToTypeRef", PropertySchema.string(
                        "ItemDefinition ID for condition return type"))
                .required(List.of("file", "source", "target"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String source = args.requireString("source", "source node ID");
        String target = args.requireString("target", "target node ID");
        String name = args.getString("name");
        String condition = args.getString("condition");
        Integer priority = args.getInt("priority");
        String evaluatesToTypeRef = args.getString("evaluatesToTypeRef");

        Bpmn2Document doc = Bpmn2Document.parse(file);

        // Validate source node exists
        Element sourceNode = doc.requireNodeExists(source);

        // Validate target node exists
        Element targetNode = doc.requireNodeExists(target);

        // Validate source != target
        if (source.equals(target)) {
            throw new IllegalArgumentException(
                    "Cannot create a self-loop: source and target are the same node '" + source + "'");
        }

        // Check no duplicate flow between same source and target
        for (Element flow : doc.listFlows()) {
            String existingSource = flow.getAttribute("sourceRef");
            String existingTarget = flow.getAttribute("targetRef");
            if (source.equals(existingSource) && target.equals(existingTarget)) {
                throw new IllegalArgumentException(
                        "Duplicate flow: a sequence flow from '" + source + "' to '" + target
                                + "' already exists (" + flow.getAttribute("id") + ").");
            }
        }

        // Validate source is not an endEvent
        if ("endEvent".equals(sourceNode.getLocalName())) {
            throw new IllegalArgumentException(
                    "Cannot add outgoing flow from endEvent '" + source + "'");
        }

        // Validate target is not a plain startEvent (signal start events are OK)
        if ("startEvent".equals(targetNode.getLocalName()) && !Bpmn2NodeHelper.hasSignalEventDefinition(targetNode)) {
            throw new IllegalArgumentException(
                    "Cannot add incoming flow to plain startEvent '" + target + "'");
        }

        // Generate flow ID
        String flowId = doc.generateId("SequenceFlow");

        // Create bpmn2:sequenceFlow element
        Element process = doc.getProcessElement();
        Element flowElement = doc.createElement(process, Bpmn2Document.NS_BPMN2, "sequenceFlow");
        flowElement.setAttribute("id", flowId);
        flowElement.setAttribute("sourceRef", source);
        flowElement.setAttribute("targetRef", target);

        // Set name if provided
        if (name != null) {
            flowElement.setAttribute("name", name);
        }

        // Add conditionExpression if condition provided
        if (condition != null) {
            Element condExpr = doc.createElement(flowElement,
                    Bpmn2Document.NS_BPMN2, "conditionExpression");
            condExpr.setAttributeNS(Bpmn2Document.NS_XSI, "xsi:type", "bpmn2:tFormalExpression");
            condExpr.setAttribute("id", doc.generateId("FormalExpression"));
            condExpr.setAttribute("language", "http://www.java.com/java");
            if (evaluatesToTypeRef != null) {
                condExpr.setAttribute("evaluatesToTypeRef", evaluatesToTypeRef);
            }
            condExpr.setTextContent(condition);
        }

        // Set tns:priority attribute (default "1" if not provided)
        String priorityValue = priority != null ? String.valueOf(priority) : "1";
        flowElement.setAttributeNS(Bpmn2Document.NS_TNS, "tns:priority", priorityValue);

        // Add bpmn2:outgoing ref to source node
        doc.createTextElement(sourceNode, Bpmn2Document.NS_BPMN2, "outgoing", flowId);

        // Add bpmn2:incoming ref to target node
        doc.createTextElement(targetNode, Bpmn2Document.NS_BPMN2, "incoming", flowId);

        doc.save();

        return AddFlowResult.builder()
                .id(flowId)
                .source(source)
                .target(target)
                .build();
    }

}
