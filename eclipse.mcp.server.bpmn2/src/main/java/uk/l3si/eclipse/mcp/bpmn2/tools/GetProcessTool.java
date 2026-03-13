package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.FlowInfo;
import uk.l3si.eclipse.mcp.bpmn2.model.NodeInfo;
import uk.l3si.eclipse.mcp.bpmn2.model.ProcessInfo;
import uk.l3si.eclipse.mcp.bpmn2.model.SignalInfo;
import uk.l3si.eclipse.mcp.bpmn2.model.VariableInfo;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.List;

public class GetProcessTool implements McpTool {

    @Override
    public String getName() {
        return "bpmn2_get_process";
    }

    @Override
    public String getDescription() {
        return "Parse a BPMN2 file and return a compact structured view of the process "
                + "including nodes, flows, variables, and signals.";
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

        Element process = doc.getProcessElement();
        Element definitions = doc.getDefinitionsElement();

        String processId = process.getAttribute("id");
        String processName = process.getAttribute("name");
        String packageName = process.getAttributeNS(Bpmn2Document.NS_TNS, "packageName");

        List<VariableInfo> variables = buildVariables(doc, definitions);
        List<SignalInfo> signals = buildSignals(doc);
        List<NodeInfo> nodes = buildNodes(doc);
        List<FlowInfo> flows = buildFlows(doc);

        return ProcessInfo.builder()
                .processId(processId)
                .processName(processName)
                .packageName(packageName)
                .variables(variables)
                .signals(signals)
                .nodes(nodes)
                .flows(flows)
                .build();
    }

    private List<VariableInfo> buildVariables(Bpmn2Document doc, Element definitions) {
        List<VariableInfo> variables = new ArrayList<>();
        for (Element prop : doc.listVariables()) {
            String name = prop.getAttribute("name");
            String itemSubjectRef = prop.getAttribute("itemSubjectRef");
            String type = resolveItemDefinitionType(definitions, itemSubjectRef);
            variables.add(VariableInfo.builder()
                    .name(name)
                    .type(type)
                    .build());
        }
        return variables;
    }

    private String resolveItemDefinitionType(Element definitions, String itemDefId) {
        if (itemDefId == null || itemDefId.isEmpty()) {
            return null;
        }
        NodeList children = definitions.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "itemDefinition".equals(el.getLocalName())
                    && itemDefId.equals(el.getAttribute("id"))) {
                return el.getAttribute("structureRef");
            }
        }
        return null;
    }

    private List<SignalInfo> buildSignals(Bpmn2Document doc) {
        List<SignalInfo> signals = new ArrayList<>();
        for (Element signal : doc.listSignals()) {
            signals.add(SignalInfo.builder()
                    .id(signal.getAttribute("id"))
                    .name(signal.getAttribute("name"))
                    .build());
        }
        return signals;
    }

    private List<NodeInfo> buildNodes(Bpmn2Document doc) {
        List<NodeInfo> nodes = new ArrayList<>();
        for (Element node : doc.listNodes()) {
            String id = node.getAttribute("id");
            String type = node.getLocalName();
            String name = node.getAttribute("name");

            NodeInfo.NodeInfoBuilder builder = NodeInfo.builder()
                    .id(id)
                    .type(type)
                    .name(name.isEmpty() ? null : name);

            switch (type) {
                case "task" -> {
                    String taskName = node.getAttributeNS(Bpmn2Document.NS_TNS, "taskName");
                    if (taskName != null && !taskName.isEmpty()) {
                        builder.taskName(taskName);
                    }
                }
                case "scriptTask" -> {
                    String script = getChildElementText(node, Bpmn2Document.NS_BPMN2, "script");
                    if (script != null && !script.isEmpty()) {
                        builder.script(script);
                    }
                }
                case "callActivity" -> {
                    String calledElement = node.getAttribute("calledElement");
                    if (calledElement != null && !calledElement.isEmpty()) {
                        builder.calledElement(calledElement);
                    }
                }
                case "exclusiveGateway" -> {
                    String direction = node.getAttribute("gatewayDirection");
                    if (direction != null && !direction.isEmpty()) {
                        builder.direction(direction.toLowerCase());
                    }
                }
                case "startEvent" -> {
                    Element signalEventDef = findFirstChildElement(node,
                            Bpmn2Document.NS_BPMN2, "signalEventDefinition");
                    if (signalEventDef != null) {
                        String signalRef = signalEventDef.getAttribute("signalRef");
                        if (signalRef != null && !signalRef.isEmpty()) {
                            builder.signalRef(signalRef);
                        }
                    }
                }
            }

            nodes.add(builder.build());
        }
        return nodes;
    }

    private List<FlowInfo> buildFlows(Bpmn2Document doc) {
        List<FlowInfo> flows = new ArrayList<>();
        for (Element flow : doc.listFlows()) {
            String id = flow.getAttribute("id");
            String sourceRef = flow.getAttribute("sourceRef");
            String targetRef = flow.getAttribute("targetRef");
            String name = flow.getAttribute("name");
            String condition = getChildElementText(flow,
                    Bpmn2Document.NS_BPMN2, "conditionExpression");

            String priority = flow.getAttributeNS(Bpmn2Document.NS_TNS, "priority");

            FlowInfo.FlowInfoBuilder builder = FlowInfo.builder()
                    .id(id)
                    .source(sourceRef)
                    .target(targetRef);

            if (name != null && !name.isEmpty()) {
                builder.name(name);
            }
            if (condition != null && !condition.isEmpty()) {
                builder.condition(condition);
            }
            if (priority != null && !priority.isEmpty()) {
                builder.priority(priority);
            }

            flows.add(builder.build());
        }
        return flows;
    }

    private static String getChildElementText(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el.getTextContent();
            }
        }
        return null;
    }

    private static Element findFirstChildElement(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }
}
