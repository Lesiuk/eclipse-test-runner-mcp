package uk.l3si.eclipse.mcp.bpmn2.tools;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import uk.l3si.eclipse.mcp.bpmn2.Bpmn2Document;
import uk.l3si.eclipse.mcp.bpmn2.model.AddImportResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ImportTool implements McpTool {

    private static final Pattern VALID_FQCN = Pattern.compile(
            "[a-zA-Z][a-zA-Z0-9]*(\\.[a-zA-Z][a-zA-Z0-9]*)+");

    @Override
    public String getName() {
        return "bpmn2_import";
    }

    @Override
    public String getDescription() {
        return "Add or remove a Java class import. "
                + "Imports let script tasks use classes by short name. "
                + "Add imports before creating script tasks that reference utility classes.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("file", PropertySchema.string("Absolute path to .bpmn2 file"))
                .property("action", PropertySchema.stringEnum("Action to perform", List.of("add", "remove")))
                .property("name", PropertySchema.string("Fully qualified Java class name"))
                .required(List.of("file", "action", "name"))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
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
        String name = args.requireString("name", "fully qualified class name");

        if (!VALID_FQCN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Invalid class name: '" + name
                            + "'. Must be a fully qualified Java class name (e.g. com.example.MyUtils).");
        }

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        // Find or create extensionElements
        Element extElements = findChildElement(process,
                Bpmn2Document.NS_BPMN2, "extensionElements");
        if (extElements == null) {
            // Insert as first child of process
            extElements = doc.createElement(process, Bpmn2Document.NS_BPMN2, "extensionElements");
            Node firstChild = process.getFirstChild();
            if (firstChild != null) {
                process.insertBefore(extElements, firstChild);
            }
        }

        // Check for duplicate import
        NodeList children = extElements.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_TNS.equals(el.getNamespaceURI())
                    && "import".equals(el.getLocalName())
                    && name.equals(el.getAttribute("name"))) {
                throw new IllegalArgumentException(
                        "Import already exists: '" + name + "'");
            }
        }

        // Create tns:import element
        Element importEl = doc.createElement(extElements, Bpmn2Document.NS_TNS, "import");
        importEl.setAttribute("name", name);

        doc.save();

        return AddImportResult.builder()
                .name(name)
                .build();
    }

    private Object executeRemove(Args args) throws Exception {
        String file = args.requireString("file", "path to .bpmn2 file");
        String name = args.requireString("name", "fully qualified class name");

        Bpmn2Document doc = Bpmn2Document.parse(file);
        Element process = doc.getProcessElement();

        // Find extensionElements
        Element extElements = findChildElement(process,
                Bpmn2Document.NS_BPMN2, "extensionElements");
        if (extElements == null) {
            throw new IllegalArgumentException("Import not found: '" + name + "'");
        }

        // Find the tns:import with matching name
        Element targetImport = null;
        NodeList children = extElements.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el
                    && Bpmn2Document.NS_TNS.equals(el.getNamespaceURI())
                    && "import".equals(el.getLocalName())
                    && name.equals(el.getAttribute("name"))) {
                targetImport = el;
                break;
            }
        }

        if (targetImport == null) {
            throw new IllegalArgumentException("Import not found: '" + name + "'");
        }

        doc.removeElement(targetImport);
        doc.save();

        return Map.of("name", name, "removed", true);
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
