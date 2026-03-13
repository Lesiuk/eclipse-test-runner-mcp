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

class AddExtensionPointToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private AddExtensionPointTool tool;

    @BeforeEach
    void setUp() {
        tool = new AddExtensionPointTool();
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
    void nameIsAddExtensionPoint() {
        assertEquals("bpmn2_add_extension_point", tool.getName());
    }

    @Test
    void addExtensionPoint() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "Web Review");
        args.addProperty("groupId", "dynamo.review");

        JsonObject result = executeAndSerialize(args);

        assertEquals("userTask", result.get("type").getAsString());

        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        Element node = doc.findNodeById(result.get("id").getAsString());
        assertNotNull(node);
        assertEquals("userTask", node.getLocalName());

        // Verify ioSpecification with 11 dataInputs
        Element ioSpec = findChildElement(node, Bpmn2Document.NS_BPMN2, "ioSpecification");
        assertNotNull(ioSpec, "ioSpecification should exist");

        int dataInputCount = 0;
        NodeList ioChildren = ioSpec.getChildNodes();
        for (int i = 0; i < ioChildren.getLength(); i++) {
            if (ioChildren.item(i) instanceof Element el
                    && "dataInput".equals(el.getLocalName())) {
                dataInputCount++;
            }
        }
        assertEquals(11, dataInputCount, "UserTask should have 11 dataInputs");

        // Verify dataOutputAssociation exists
        Element dataOutputAssoc = findChildElement(node,
                Bpmn2Document.NS_BPMN2, "dataOutputAssociation");
        assertNotNull(dataOutputAssoc, "UserTask should have dataOutputAssociation");
    }

    @Test
    void addExtensionPointWithoutGroupId() throws Exception {
        Path file = copyTestResource();
        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("name", "No Group Review");

        JsonObject result = executeAndSerialize(args);

        assertEquals("userTask", result.get("type").getAsString());
        assertNotNull(result.get("id"));
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
