package uk.l3si.eclipse.mcp.bpmn2;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashSet;
import java.util.Set;

/**
 * Generates unique IDs for BPMN2 elements by scanning existing IDs in a document
 * and producing the next available {@code {prefix}_{N}} value.
 */
public class Bpmn2IdGenerator {

    private final Set<String> usedIds = new HashSet<>();

    public Bpmn2IdGenerator(Document document) {
        scanIds(document);
    }

    private void scanIds(Document document) {
        NodeList allElements = document.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element element) {
                String id = element.getAttribute("id");
                if (id != null && !id.isEmpty()) {
                    usedIds.add(id);
                }
            }
        }
    }

    /**
     * Generates the next available ID for the given prefix.
     * <p>
     * The pattern is {@code {prefix}_{N}} where N starts at 1 and increments
     * until an unused ID is found. The generated ID is immediately reserved
     * so that subsequent calls will not return the same value.
     *
     * @param prefix the element type prefix (e.g. "Task", "StartEvent", "SequenceFlow")
     * @return a unique ID string
     */
    public String generate(String prefix) {
        int n = 1;
        while (usedIds.contains(prefix + "_" + n)) {
            n++;
        }
        String id = prefix + "_" + n;
        usedIds.add(id);
        return id;
    }
}
