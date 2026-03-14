package uk.l3si.eclipse.mcp.bpmn2.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.tools.Args;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AddStartEventToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddStartEventTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddStartEventTool();
    }

    private Path copyTestResource() throws Exception {
        Path target = tempDir.resolve("test-flow.bpmn2");
        try (InputStream in = getClass().getResourceAsStream("/test-flow.bpmn2")) {
            assertNotNull(in, "Test resource not found");
            Files.copy(in, target);
        }
        return target;
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsAddStartEvent() {
        assertEquals("bpmn2_start_event", tool.getName());
    }

    @Test
    void addStartEventWithSignalRef() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Signal Start");
        args.addProperty("signalRef", "Signal_1");

        JsonObject result = executeAndSerialize(args);

        assertEquals("startEvent", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);

        Element signalEventDef = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "signalEventDefinition");
        assertNotNull(signalEventDef, "signalEventDefinition child should exist");
        assertEquals("Signal_1", signalEventDef.getAttribute("signalRef"));

        Element dataOutput = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "dataOutput");
        assertNotNull(dataOutput, "signal start event should have dataOutput");

        Element dataOutputAssoc = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        assertNotNull(dataOutputAssoc, "signal start event should have dataOutputAssociation");

        Element outputSet = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "outputSet");
        assertNotNull(outputSet, "signal start event should have outputSet");
    }

    @Test
    void duplicatePlainStartEventThrowsError() throws Exception {
        // test-flow.bpmn2 already has StartEvent_1 (plain, no signal)
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Second Start");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("plain startEvent"), ex.getMessage());
    }

    @Test
    void invalidSignalRefThrowsError() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Bad Signal Start");
        args.addProperty("signalRef", "Signal_999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("Signal not found"), ex.getMessage());
    }

    private static Element findChildElement(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && ns.equals(el.getNamespaceURI())
                    && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }
}
