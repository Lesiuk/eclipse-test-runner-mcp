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

class TextAnnotationToolTest {

    private static final Gson GSON = new Gson();

    @TempDir
    Path tempDir;

    private TextAnnotationTool tool;

    @BeforeEach
    void setUp() {
        tool = new TextAnnotationTool();
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
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    @Test
    void nameIsTextAnnotation() {
        assertEquals("bpmn2_text_annotation", tool.getName());
    }

    // ---- Add tests ----

    @Test
    void addAnnotationWithoutAttachment() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("text", "This is a comment");

        JsonObject result = executeAndSerialize(args);

        assertNotNull(result.get("id"));
        assertFalse(result.has("associationId"));
    }

    @Test
    void addAnnotationAttachedToNode() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("text", "Handles the main request");
        args.addProperty("attachTo", "Task_1");

        JsonObject result = executeAndSerialize(args);

        assertNotNull(result.get("id"));
        assertNotNull(result.get("associationId"));
        assertFalse(result.get("associationId").isJsonNull());
    }

    @Test
    void attachToNonExistentNodeThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "add");
        args.addProperty("text", "Some note");
        args.addProperty("attachTo", "NonExistent_1");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Target element not found"), ex.getMessage());
    }

    // ---- Remove tests ----

    @Test
    void removeTextAnnotationWithoutAssociation() throws Exception {
        Path file = copyTestResource();

        // Add a text annotation without attachment
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("action", "add");
        addArgs.addProperty("text", "Test comment");
        addArgs.addProperty("id", "TextAnnotation_1");
        tool.execute(new Args(addArgs), message -> {});

        // Remove it
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
        removeArgs.addProperty("id", "TextAnnotation_1");

        JsonObject result = executeAndSerialize(removeArgs);

        assertEquals("TextAnnotation_1", result.get("id").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Verify it's gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNull(findTextAnnotationById(doc, "TextAnnotation_1"),
                "Text annotation should have been removed");
    }

    @Test
    void removeTextAnnotationWithAssociation() throws Exception {
        Path file = copyTestResource();

        // Add a text annotation attached to Task_1
        JsonObject addArgs = new JsonObject();
        addArgs.addProperty("file", file.toString());
        addArgs.addProperty("action", "add");
        addArgs.addProperty("text", "Attached note");
        addArgs.addProperty("attachTo", "Task_1");
        addArgs.addProperty("id", "TextAnnotation_2");

        JsonObject addResult = executeAndSerialize(addArgs);
        String associationId = addResult.get("associationId").getAsString();

        // Remove the annotation
        JsonObject removeArgs = new JsonObject();
        removeArgs.addProperty("file", file.toString());
        removeArgs.addProperty("action", "remove");
        removeArgs.addProperty("id", "TextAnnotation_2");

        JsonObject result = executeAndSerialize(removeArgs);

        assertEquals("TextAnnotation_2", result.get("id").getAsString());
        assertTrue(result.get("removed").getAsBoolean());

        // Verify both annotation and association are gone
        Bpmn2Document doc = Bpmn2Document.parse(file.toString());
        assertNull(findTextAnnotationById(doc, "TextAnnotation_2"),
                "Text annotation should have been removed");
        assertNull(findAssociationById(doc, associationId),
                "Association should have been removed");
    }

    @Test
    void textAnnotationNotFoundThrowsError() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "remove");
        args.addProperty("id", "NonExistent_Annotation");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Text annotation not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NonExistent_Annotation"), ex.getMessage());
    }

    @Test
    void invalidAction() throws Exception {
        Path file = copyTestResource();

        JsonObject args = new JsonObject();
        args.addProperty("file", file.toString());
        args.addProperty("action", "update");
        args.addProperty("text", "Some note");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("Invalid action"), ex.getMessage());
    }

    // ---- Helpers ----

    private static Element findTextAnnotationById(Bpmn2Document doc, String id) {
        Element process = doc.getProcessElement();
        NodeList children = process.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "textAnnotation".equals(el.getLocalName())
                    && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }

    private static Element findAssociationById(Bpmn2Document doc, String id) {
        Element process = doc.getProcessElement();
        NodeList children = process.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_BPMN2.equals(el.getNamespaceURI())
                    && "association".equals(el.getLocalName())
                    && id.equals(el.getAttribute("id"))) {
                return el;
            }
        }
        return null;
    }
}
