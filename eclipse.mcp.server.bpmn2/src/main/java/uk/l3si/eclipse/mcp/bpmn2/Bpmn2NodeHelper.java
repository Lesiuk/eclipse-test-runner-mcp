package uk.l3si.eclipse.mcp.bpmn2;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;

/**
 * Static utility methods shared by the domain-specific node tools.
 * Extracted from the former monolithic AddNodeTool.
 */
public final class Bpmn2NodeHelper {

    private Bpmn2NodeHelper() {
    }

    /**
     * Adds extensionElements with a metaData element for "elementname".
     */
    public static Element addExtensionElements(Bpmn2Document doc, Element parent, String name) {
        Element extElements = doc.createElement(parent,
                Bpmn2Document.NS_BPMN2, "extensionElements");
        addMetaData(doc, extElements, "elementname", name);
        return extElements;
    }

    /**
     * Adds a tns:metaData element with a tns:metaValue child using CDATA.
     */
    public static void addMetaData(Bpmn2Document doc, Element parent,
                                    String metaName, String metaValue) {
        Element metaData = doc.createElement(parent, Bpmn2Document.NS_TNS, "metaData");
        metaData.setAttribute("name", metaName);
        doc.createCDataTextElement(metaData, Bpmn2Document.NS_TNS, "metaValue", metaValue);
    }

    /**
     * Adds the ioSpecification boilerplate for task nodes (using taskCommandFlow).
     */
    public static void addTaskIoSpecification(Bpmn2Document doc, Element taskElement) {
        String ioSpecId = doc.generateId("InputOutputSpecification");
        String dataInputId = doc.generateId("DataInput");
        String dataOutputId = doc.generateId("DataOutput");
        String inputSetId = doc.generateId("InputSet");
        String outputSetId = doc.generateId("OutputSet");
        String dataInputAssocId = doc.generateId("DataInputAssociation");
        String dataOutputAssocId = doc.generateId("DataOutputAssociation");

        Element ioSpec = doc.createElement(taskElement,
                Bpmn2Document.NS_BPMN2, "ioSpecification");
        ioSpec.setAttribute("id", ioSpecId);

        Element dataInput = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataInput");
        dataInput.setAttribute("id", dataInputId);
        dataInput.setAttribute("itemSubjectRef", "ItemDefinition_1");
        dataInput.setAttribute("name", "taskCommandFlow");

        Element dataOutput = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataOutput");
        dataOutput.setAttribute("id", dataOutputId);
        dataOutput.setAttribute("itemSubjectRef", "ItemDefinition_1");
        dataOutput.setAttribute("name", "taskCommandFlow");

        Element inputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "inputSet");
        inputSet.setAttribute("id", inputSetId);
        doc.createTextElement(inputSet, Bpmn2Document.NS_BPMN2, "dataInputRefs", dataInputId);

        Element outputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "outputSet");
        outputSet.setAttribute("id", outputSetId);
        doc.createTextElement(outputSet, Bpmn2Document.NS_BPMN2, "dataOutputRefs", dataOutputId);

        Element inputAssoc = doc.createElement(taskElement,
                Bpmn2Document.NS_BPMN2, "dataInputAssociation");
        inputAssoc.setAttribute("id", dataInputAssocId);
        doc.createTextElement(inputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                "processCommandFlow");
        doc.createTextElement(inputAssoc, Bpmn2Document.NS_BPMN2, "targetRef", dataInputId);

        Element outputAssoc = doc.createElement(taskElement,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        outputAssoc.setAttribute("id", dataOutputAssocId);
        doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef", dataOutputId);
        doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                "processCommandFlow");
    }

    /**
     * Adds the ioSpecification boilerplate for callActivity nodes (using processCommandFlow).
     */
    public static void addCallActivityIoSpecification(Bpmn2Document doc, Element element) {
        String ioSpecId = doc.generateId("InputOutputSpecification");
        String dataInputId = doc.generateId("DataInput");
        String dataOutputId = doc.generateId("DataOutput");
        String inputSetId = doc.generateId("InputSet");
        String outputSetId = doc.generateId("OutputSet");
        String dataInputAssocId = doc.generateId("DataInputAssociation");
        String dataOutputAssocId = doc.generateId("DataOutputAssociation");

        Element ioSpec = doc.createElement(element,
                Bpmn2Document.NS_BPMN2, "ioSpecification");
        ioSpec.setAttribute("id", ioSpecId);

        Element dataInput = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataInput");
        dataInput.setAttribute("id", dataInputId);
        dataInput.setAttribute("itemSubjectRef", "ItemDefinition_1");
        dataInput.setAttribute("name", "processCommandFlow");

        Element dataOutput = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataOutput");
        dataOutput.setAttribute("id", dataOutputId);
        dataOutput.setAttribute("itemSubjectRef", "ItemDefinition_1");
        dataOutput.setAttribute("name", "processCommandFlow");

        Element inputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "inputSet");
        inputSet.setAttribute("id", inputSetId);
        doc.createTextElement(inputSet, Bpmn2Document.NS_BPMN2, "dataInputRefs", dataInputId);

        Element outputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "outputSet");
        outputSet.setAttribute("id", outputSetId);
        doc.createTextElement(outputSet, Bpmn2Document.NS_BPMN2, "dataOutputRefs", dataOutputId);

        Element inputAssoc = doc.createElement(element,
                Bpmn2Document.NS_BPMN2, "dataInputAssociation");
        inputAssoc.setAttribute("id", dataInputAssocId);
        doc.createTextElement(inputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                "processCommandFlow");
        doc.createTextElement(inputAssoc, Bpmn2Document.NS_BPMN2, "targetRef", dataInputId);

        Element outputAssoc = doc.createElement(element,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        outputAssoc.setAttribute("id", dataOutputAssocId);
        doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "sourceRef", dataOutputId);
        doc.createTextElement(outputAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                "processCommandFlow");
    }

    /**
     * Adds the full jBPM human task ioSpecification for userTask nodes.
     */
    public static void addUserTaskIoSpecification(Bpmn2Document doc, Element element,
                                                   String taskDisplayName, String groupId) {
        String ioSpecId = doc.generateId("InputOutputSpecification");
        String inputSetId = doc.generateId("InputSet");
        String outputSetId = doc.generateId("OutputSet");

        String diTaskName = doc.generateId("DataInput");
        String diPriority = doc.generateId("DataInput");
        String diComment = doc.generateId("DataInput");
        String diDescription = doc.generateId("DataInput");
        String diGroupId = doc.generateId("DataInput");
        String diSkippable = doc.generateId("DataInput");
        String diContent = doc.generateId("DataInput");
        String diLocale = doc.generateId("DataInput");
        String diCreatedBy = doc.generateId("DataInput");
        String diTaskCommandFlow = doc.generateId("DataInput");
        String diTypeHumanTask = doc.generateId("DataInput");

        String doTaskCommandFlow = doc.generateId("DataOutput");

        Element ioSpec = doc.createElement(element,
                Bpmn2Document.NS_BPMN2, "ioSpecification");
        ioSpec.setAttribute("id", ioSpecId);

        createDataInput(doc, ioSpec, diTaskName, "ItemDefinition_1", "TaskName");
        createDataInput(doc, ioSpec, diPriority, "ItemDefinition_2", "Priority");
        createDataInput(doc, ioSpec, diComment, "ItemDefinition_1", "Comment");
        createDataInput(doc, ioSpec, diDescription, "ItemDefinition_1", "Description");
        createDataInput(doc, ioSpec, diGroupId, "ItemDefinition_1", "GroupId");
        createDataInput(doc, ioSpec, diSkippable, "ItemDefinition_3", "Skippable");
        createDataInput(doc, ioSpec, diContent, "ItemDefinition_1", "Content");
        createDataInput(doc, ioSpec, diLocale, "ItemDefinition_1", "Locale");
        createDataInput(doc, ioSpec, diCreatedBy, "ItemDefinition_1", "CreatedBy");
        createDataInput(doc, ioSpec, diTaskCommandFlow, "ItemDefinition_1", "taskCommandFlow");
        createDataInput(doc, ioSpec, diTypeHumanTask, "ItemDefinition_1", "TypeHumanTask");

        Element doEl = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataOutput");
        doEl.setAttribute("id", doTaskCommandFlow);
        doEl.setAttribute("itemSubjectRef", "ItemDefinition_1");
        doEl.setAttribute("name", "taskCommandFlow");

        Element inputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "inputSet");
        inputSet.setAttribute("id", inputSetId);
        for (String diId : List.of(diTaskName, diPriority, diComment, diDescription,
                diGroupId, diSkippable, diContent, diLocale, diCreatedBy,
                diTaskCommandFlow, diTypeHumanTask)) {
            doc.createTextElement(inputSet, Bpmn2Document.NS_BPMN2, "dataInputRefs", diId);
        }

        Element outputSet = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "outputSet");
        outputSet.setAttribute("id", outputSetId);
        doc.createTextElement(outputSet, Bpmn2Document.NS_BPMN2, "dataOutputRefs",
                doTaskCommandFlow);

        addAssignmentAssociation(doc, element, diTaskName, "Task Name");
        addAssignmentAssociation(doc, element, diPriority, "1");
        addEmptyAssociation(doc, element, diComment);
        addEmptyAssociation(doc, element, diDescription);
        addAssignmentAssociation(doc, element, diGroupId,
                groupId != null ? groupId : "");
        addAssignmentAssociation(doc, element, diSkippable, "true");
        addEmptyAssociation(doc, element, diContent);
        addAssignmentAssociation(doc, element, diLocale, "en-UK");
        addEmptyAssociation(doc, element, diCreatedBy);

        String tcfAssocId = doc.generateId("DataInputAssociation");
        Element tcfAssoc = doc.createElement(element,
                Bpmn2Document.NS_BPMN2, "dataInputAssociation");
        tcfAssoc.setAttribute("id", tcfAssocId);
        doc.createTextElement(tcfAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                "processCommandFlow");
        doc.createTextElement(tcfAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                diTaskCommandFlow);

        addAssignmentAssociation(doc, element, diTypeHumanTask, "WebExtensionPoint");

        String doAssocId = doc.generateId("DataOutputAssociation");
        Element doAssoc = doc.createElement(element,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        doAssoc.setAttribute("id", doAssocId);
        doc.createTextElement(doAssoc, Bpmn2Document.NS_BPMN2, "sourceRef",
                doTaskCommandFlow);
        doc.createTextElement(doAssoc, Bpmn2Document.NS_BPMN2, "targetRef",
                "processCommandFlow");
    }

    /**
     * Creates a dataInput element inside an ioSpecification.
     */
    public static void createDataInput(Bpmn2Document doc, Element ioSpec,
                                        String id, String itemSubjectRef, String name) {
        Element di = doc.createElement(ioSpec, Bpmn2Document.NS_BPMN2, "dataInput");
        di.setAttribute("id", id);
        di.setAttribute("itemSubjectRef", itemSubjectRef);
        di.setAttribute("name", name);
    }

    /**
     * Creates a dataInputAssociation with an assignment (from literal value to dataInput).
     */
    public static void addAssignmentAssociation(Bpmn2Document doc, Element parent,
                                                 String dataInputId, String value) {
        String assocId = doc.generateId("DataInputAssociation");
        String assignmentId = doc.generateId("Assignment");
        String fromId = doc.generateId("FormalExpression");
        String toId = doc.generateId("FormalExpression");

        Element assoc = doc.createElement(parent,
                Bpmn2Document.NS_BPMN2, "dataInputAssociation");
        assoc.setAttribute("id", assocId);
        doc.createTextElement(assoc, Bpmn2Document.NS_BPMN2, "targetRef", dataInputId);

        Element assignment = doc.createElement(assoc, Bpmn2Document.NS_BPMN2, "assignment");
        assignment.setAttribute("id", assignmentId);

        Element from = doc.createElement(assignment, Bpmn2Document.NS_BPMN2, "from");
        from.setAttributeNS(Bpmn2Document.NS_XSI, "xsi:type", "bpmn2:tFormalExpression");
        from.setAttribute("id", fromId);
        from.setTextContent(value);

        Element to = doc.createElement(assignment, Bpmn2Document.NS_BPMN2, "to");
        to.setAttributeNS(Bpmn2Document.NS_XSI, "xsi:type", "bpmn2:tFormalExpression");
        to.setAttribute("id", toId);
        to.setTextContent(dataInputId);
    }

    /**
     * Creates a dataInputAssociation with only a targetRef (no assignment).
     */
    public static void addEmptyAssociation(Bpmn2Document doc, Element parent,
                                            String dataInputId) {
        String assocId = doc.generateId("DataInputAssociation");
        Element assoc = doc.createElement(parent,
                Bpmn2Document.NS_BPMN2, "dataInputAssociation");
        assoc.setAttribute("id", assocId);
        doc.createTextElement(assoc, Bpmn2Document.NS_BPMN2, "targetRef", dataInputId);
    }

    /**
     * Checks if an element has a signalEventDefinition child.
     */
    public static boolean hasSignalEventDefinition(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "signalEventDefinition".equals(el.getLocalName())) {
                return true;
            }
        }
        return false;
    }
}
