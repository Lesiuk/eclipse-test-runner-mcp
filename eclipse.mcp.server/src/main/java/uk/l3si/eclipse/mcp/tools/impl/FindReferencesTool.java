package uk.l3si.eclipse.mcp.tools.impl;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import uk.l3si.eclipse.mcp.model.FindReferencesResult;
import uk.l3si.eclipse.mcp.model.ReferenceFileGroup;
import uk.l3si.eclipse.mcp.model.ReferenceMatch;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FindReferencesTool implements IMcpTool {

    @Override
    public String getName() {
        return "find_references";
    }

    @Override
    public String getDescription() {
        return "Find all references to a Java class, method, or field across all open projects in the Eclipse workspace. "
             + "Use this to understand how code is used before refactoring, renaming, or deleting it. "
             + "Uses Eclipse's semantic search (not text grep) — finds actual usages, not just name matches. "
             + "Returns results grouped by file with line numbers and source line context.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("class", PropertySchema.string("Fully qualified class name (e.g. 'com.example.MyService')"))
                .property("member", PropertySchema.string(
                        "Optional: method or field name to find references to. "
                        + "If omitted, finds references to the class itself."))
                .required(List.of("class"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String className = args.requireString("class", "fully qualified class name");
        String memberName = args.getString("member");

        IType type = findType(className);
        IJavaElement element = resolveElement(type, className, memberName);
        String elementLabel = formatElementLabel(element, className, memberName);

        SearchPattern pattern = createPattern(type, memberName);
        if (pattern == null) {
            throw new IllegalArgumentException(
                    "Cannot create search pattern for: " + elementLabel);
        }

        IJavaSearchScope scope = SearchEngine.createWorkspaceScope();
        SearchEngine engine = new SearchEngine();
        CollectingRequestor requestor = new CollectingRequestor();

        engine.search(
                pattern,
                new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() },
                scope,
                requestor,
                new NullProgressMonitor());

        return buildResult(elementLabel, requestor.getMatches());
    }

    private IType findType(String className) throws Exception {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        for (IProject project : projects) {
            if (!project.isOpen() || !project.hasNature(JavaCore.NATURE_ID)) {
                continue;
            }
            IJavaProject javaProject = JavaCore.create(project);
            IType type = javaProject.findType(className);
            if (type != null && type.exists()) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Class '" + className + "' not found in any open Java project. "
                + "Check that the fully qualified name is correct (e.g. 'com.example.MyService').");
    }

    private IJavaElement resolveElement(IType type, String className, String memberName) throws Exception {
        if (memberName == null) {
            return type;
        }

        // Try field first
        IField field = type.getField(memberName);
        if (field != null && field.exists()) {
            return field;
        }

        // Try methods (all overloads)
        IMethod[] methods = type.getMethods();
        List<IMethod> matching = new ArrayList<>();
        for (IMethod method : methods) {
            if (method.getElementName().equals(memberName)) {
                matching.add(method);
            }
        }

        if (matching.size() == 1) {
            return matching.get(0);
        }
        if (matching.size() > 1) {
            // Multiple overloads — search for all by using the first and OR-ing patterns
            SearchPattern combined = null;
            for (IMethod method : matching) {
                SearchPattern p = SearchPattern.createPattern(method, IJavaSearchConstants.REFERENCES);
                combined = combined == null ? p : SearchPattern.createOrPattern(combined, p);
            }
            return matching.get(0); // Return first for label; we'll handle combined pattern differently
        }

        throw new IllegalArgumentException(
                "Member '" + memberName + "' not found in class '" + className + "'. "
                + "Available members: " + getAvailableMembers(type));
    }

    /**
     * Overridden execute that handles multiple method overloads.
     */
    private SearchPattern createPattern(IType type, String memberName) throws Exception {
        if (memberName == null) {
            return SearchPattern.createPattern(type, IJavaSearchConstants.REFERENCES);
        }

        // Try field first
        IField field = type.getField(memberName);
        if (field != null && field.exists()) {
            return SearchPattern.createPattern(field, IJavaSearchConstants.REFERENCES);
        }

        // Combine all method overloads into one OR pattern
        IMethod[] methods = type.getMethods();
        SearchPattern combined = null;
        for (IMethod method : methods) {
            if (method.getElementName().equals(memberName)) {
                SearchPattern p = SearchPattern.createPattern(method, IJavaSearchConstants.REFERENCES);
                combined = combined == null ? p : SearchPattern.createOrPattern(combined, p);
            }
        }
        return combined;
    }

    private String formatElementLabel(IJavaElement element, String className, String memberName) {
        if (memberName == null) {
            return className;
        }
        if (element instanceof IMethod method) {
            String[] paramTypes = method.getParameterTypes();
            StringBuilder sb = new StringBuilder(className).append(".").append(memberName).append("(");
            for (int i = 0; i < paramTypes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(org.eclipse.jdt.core.Signature.toString(paramTypes[i]));
            }
            sb.append(")");
            return sb.toString();
        }
        return className + "." + memberName;
    }

    private String getAvailableMembers(IType type) throws Exception {
        List<String> names = new ArrayList<>();
        for (IField field : type.getFields()) {
            names.add(field.getElementName());
        }
        for (IMethod method : type.getMethods()) {
            String name = method.getElementName();
            if (!names.contains(name)) {
                names.add(name + "()");
            }
        }
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }

    private FindReferencesResult buildResult(String elementLabel, List<SearchMatch> matches) {
        // Group by containing type
        Map<String, List<ReferenceMatch>> grouped = new LinkedHashMap<>();

        for (SearchMatch match : matches) {
            String containingClass = getContainingClassName(match);
            String sourceLine = getSourceLine(match);
            int line = getLineNumber(match);

            grouped.computeIfAbsent(containingClass, k -> new ArrayList<>())
                    .add(ReferenceMatch.builder()
                            .line(line)
                            .source(sourceLine)
                            .build());
        }

        List<ReferenceFileGroup> files = new ArrayList<>();
        for (Map.Entry<String, List<ReferenceMatch>> entry : grouped.entrySet()) {
            files.add(ReferenceFileGroup.builder()
                    .className(entry.getKey())
                    .references(entry.getValue())
                    .build());
        }

        return FindReferencesResult.builder()
                .element(elementLabel)
                .totalReferences(matches.size())
                .files(files)
                .build();
    }

    private String getContainingClassName(SearchMatch match) {
        IJavaElement element = (IJavaElement) match.getElement();
        if (element != null) {
            IType type = (IType) element.getAncestor(IJavaElement.TYPE);
            if (type != null) {
                return type.getFullyQualifiedName();
            }
        }
        if (match.getResource() != null) {
            return match.getResource().getFullPath().toString();
        }
        return "(unknown)";
    }

    private int getLineNumber(SearchMatch match) {
        IJavaElement element = (IJavaElement) match.getElement();
        if (element != null) {
            try {
                ITypeRoot typeRoot = (ITypeRoot) element.getAncestor(IJavaElement.COMPILATION_UNIT);
                if (typeRoot == null) {
                    typeRoot = (ITypeRoot) element.getAncestor(IJavaElement.CLASS_FILE);
                }
                if (typeRoot != null) {
                    String source = typeRoot.getSource();
                    if (source != null && match.getOffset() <= source.length()) {
                        int line = 1;
                        for (int i = 0; i < match.getOffset() && i < source.length(); i++) {
                            if (source.charAt(i) == '\n') line++;
                        }
                        return line;
                    }
                }
            } catch (Exception e) {
                // fall through
            }
        }
        return -1;
    }

    private String getSourceLine(SearchMatch match) {
        IJavaElement element = (IJavaElement) match.getElement();
        if (element != null) {
            try {
                ITypeRoot typeRoot = (ITypeRoot) element.getAncestor(IJavaElement.COMPILATION_UNIT);
                if (typeRoot == null) {
                    typeRoot = (ITypeRoot) element.getAncestor(IJavaElement.CLASS_FILE);
                }
                if (typeRoot != null) {
                    String source = typeRoot.getSource();
                    if (source != null && match.getOffset() <= source.length()) {
                        // Find the start of the line
                        int lineStart = source.lastIndexOf('\n', match.getOffset() - 1) + 1;
                        // Find the end of the line
                        int lineEnd = source.indexOf('\n', match.getOffset());
                        if (lineEnd == -1) lineEnd = source.length();
                        return source.substring(lineStart, lineEnd).trim();
                    }
                }
            } catch (Exception e) {
                // fall through
            }
        }
        return null;
    }

    private static class CollectingRequestor extends SearchRequestor {
        private final List<SearchMatch> matches = new ArrayList<>();

        @Override
        public void acceptSearchMatch(SearchMatch match) {
            if (match.getAccuracy() == SearchMatch.A_ACCURATE) {
                matches.add(match);
            }
        }

        List<SearchMatch> getMatches() {
            return matches;
        }
    }
}
