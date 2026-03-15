package uk.l3si.eclipse.mcp.bpmn2;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class Bpmn2IdGeneratorTest {

    private Document parseTestResource() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(
                getClass().getResourceAsStream("/test-flow.bpmn2"));
    }

    private Document emptyDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().newDocument();
    }

    @Test
    void skipsExistingTaskId() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("Task_2", gen.generate("Task"));
    }

    @Test
    void skipsExistingStartEventId() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("StartEvent_2", gen.generate("StartEvent"));
    }

    @Test
    void returnsFirstIdWhenNoneExist() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("ExclusiveGateway_1", gen.generate("ExclusiveGateway"));
    }

    @Test
    void multipleCallsIncrement() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());

        assertEquals("Foo_1", gen.generate("Foo"));
        assertEquals("Foo_2", gen.generate("Foo"));
        assertEquals("Foo_3", gen.generate("Foo"));
    }

    @Test
    void emptyDocumentStartsAtOne() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(emptyDocument());

        assertEquals("Task_1", gen.generate("Task"));
        assertEquals("StartEvent_1", gen.generate("StartEvent"));
        assertEquals("SequenceFlow_1", gen.generate("SequenceFlow"));
    }

    @Test
    void fillsGapInExistingIds() throws Exception {
        // Task_1 exists in the test resource, but Task_2 does not.
        // After generating Task_2, the next call should return Task_3.
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());

        assertEquals("Task_2", gen.generate("Task"));
        assertEquals("Task_3", gen.generate("Task"));
    }

    @Test
    void skipsExistingEndEventId() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("EndEvent_2", gen.generate("EndEvent"));
    }

    @Test
    void skipsExistingSequenceFlowIds() throws Exception {
        // SequenceFlow_1 and SequenceFlow_2 exist in the test resource
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("SequenceFlow_3", gen.generate("SequenceFlow"));
    }

    @Test
    void skipsExistingSignalId() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("Signal_2", gen.generate("Signal"));
    }

    @Test
    void skipsExistingItemDefinitionId() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(parseTestResource());
        assertEquals("ItemDefinition_2", gen.generate("ItemDefinition"));
    }

    @Test
    void differentPrefixesAreIndependent() throws Exception {
        Bpmn2IdGenerator gen = new Bpmn2IdGenerator(emptyDocument());

        assertEquals("Task_1", gen.generate("Task"));
        assertEquals("ScriptTask_1", gen.generate("ScriptTask"));
        assertEquals("Task_2", gen.generate("Task"));
        assertEquals("ScriptTask_2", gen.generate("ScriptTask"));
    }
}
