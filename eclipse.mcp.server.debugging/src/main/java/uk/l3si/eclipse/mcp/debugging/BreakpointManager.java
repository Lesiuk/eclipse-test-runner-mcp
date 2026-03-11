package uk.l3si.eclipse.mcp.debugging;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.debug.core.JDIDebugModel;
import org.eclipse.jdt.internal.debug.core.breakpoints.ValidBreakpointLocationLocator;
import uk.l3si.eclipse.mcp.debugging.model.BreakpointInfo;
import uk.l3si.eclipse.mcp.debugging.model.BreakpointResult;
import uk.l3si.eclipse.mcp.debugging.model.ListBreakpointsResult;
import uk.l3si.eclipse.mcp.debugging.model.RemoveBreakpointResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around Eclipse's breakpoint manager.
 * Uses Eclipse marker IDs directly — no internal tracking needed.
 */
public class BreakpointManager {

    /**
     * Create a line breakpoint and return its marker ID.
     * Uses Eclipse's ValidBreakpointLocationLocator (same as the Toggle Breakpoint action)
     * to validate/adjust the line to the nearest executable location.
     */
    public BreakpointResult setBreakpoint(String className, int line, String condition) throws Exception {
        if (condition != null && !condition.isBlank()) {
            validateConditionSyntax(condition);
        }

        IType type = findType(className);
        IResource resource = type.getUnderlyingResource();
        if (resource == null) {
            throw new IllegalArgumentException(
                    "Class '" + className + "' has no underlying resource (may be a binary class).");
        }

        int validatedLine = line;
        ICompilationUnit cu = type.getCompilationUnit();
        if (cu != null && cu.getSource() != null) {
            validatedLine = validateBreakpointLocation(cu, line);
        }

        IJavaLineBreakpoint bp = JDIDebugModel.createLineBreakpoint(
                resource, className, validatedLine, -1, -1, 0, true, null);

        if (condition != null && !condition.isBlank()) {
            bp.setCondition(condition);
            bp.setConditionEnabled(true);
        }

        BreakpointResult.BreakpointResultBuilder resultBuilder = BreakpointResult.builder()
                .id(bp.getMarker().getId())
                .className(className)
                .line(validatedLine)
                .condition(condition != null && !condition.isBlank() ? condition : null)
                .enabled(bp.isEnabled());

        if (validatedLine != line) {
            resultBuilder.adjustedFrom(line);
        }

        return resultBuilder.build();
    }

    /**
     * Remove a breakpoint by marker ID.
     */
    public RemoveBreakpointResult removeBreakpoint(long id) throws Exception {
        IJavaLineBreakpoint bp = findBreakpointById(id);
        if (bp == null) {
            throw new IllegalArgumentException(
                    "Breakpoint not found with ID: " + id + ". Use 'list_breakpoints' to see all current breakpoints and their IDs.");
        }
        bp.delete();
        return RemoveBreakpointResult.builder()
                .removed(true)
                .id(id)
                .build();
    }

    /**
     * List all Java line breakpoints in the workspace.
     */
    public ListBreakpointsResult listBreakpoints() throws Exception {
        IBreakpoint[] allBreakpoints = DebugPlugin.getDefault()
                .getBreakpointManager().getBreakpoints(JDIDebugModel.getPluginIdentifier());

        List<BreakpointInfo> list = new ArrayList<>();
        for (IBreakpoint bp : allBreakpoints) {
            if (!(bp instanceof IJavaLineBreakpoint lineBp)) continue;
            if (lineBp.getMarker() == null) continue;

            BreakpointInfo.BreakpointInfoBuilder infoBuilder = BreakpointInfo.builder()
                    .id(lineBp.getMarker().getId());
            try {
                infoBuilder.className(lineBp.getTypeName())
                        .line(lineBp.getLineNumber());
                String cond = lineBp.getCondition();
                if (cond != null && !cond.isBlank()) {
                    infoBuilder.condition(cond);
                }
                infoBuilder.enabled(lineBp.isEnabled())
                        .hitCount(lineBp.getHitCount());
            } catch (Exception e) {
                infoBuilder.error(e.getMessage());
            }
            list.add(infoBuilder.build());
        }
        return ListBreakpointsResult.builder()
                .breakpoints(list)
                .build();
    }

    private IJavaLineBreakpoint findBreakpointById(long id) {
        IBreakpoint[] allBreakpoints = DebugPlugin.getDefault()
                .getBreakpointManager().getBreakpoints(JDIDebugModel.getPluginIdentifier());
        for (IBreakpoint bp : allBreakpoints) {
            if (bp instanceof IJavaLineBreakpoint lineBp
                    && bp.getMarker() != null
                    && bp.getMarker().getId() == id) {
                return lineBp;
            }
        }
        return null;
    }

    /**
     * Validate that a breakpoint condition is syntactically valid Java.
     * Wraps the condition in a method body and parses it as a compilation unit.
     */
    static void validateConditionSyntax(String condition) {
        String source = "class _V { boolean _c() { return " + condition + "; } }";
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(source.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        if (cu.getProblems().length > 0) {
            throw new IllegalArgumentException(
                    "Breakpoint condition has syntax errors: '" + condition + "'. "
                    + "Must be a valid Java boolean expression (e.g. 'i == 5', 'name.equals(\"test\")').");
        }
    }

    /**
     * Validate a breakpoint location using Eclipse's own ValidBreakpointLocationLocator
     * (the same infrastructure used by Eclipse's "Toggle Breakpoint" action).
     * Returns the validated line number, which may be adjusted to the nearest executable line.
     */
    private int validateBreakpointLocation(ICompilationUnit cu, int requestedLine) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(cu);
        parser.setResolveBindings(true);
        CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

        ValidBreakpointLocationLocator locator = new ValidBreakpointLocationLocator(
                astRoot, requestedLine, true, true);
        astRoot.accept(locator);

        if (locator.getLocationType() == ValidBreakpointLocationLocator.LOCATION_NOT_FOUND) {
            throw new IllegalArgumentException(
                    "Line " + requestedLine + " is not a valid breakpoint location. "
                    + "Breakpoints can only be set on lines containing executable code "
                    + "(not comments, blank lines, imports, or declarations without initializers).");
        }

        return locator.getLineLocation();
    }

    /**
     * Find the IType for a fully qualified class name by searching all Java projects.
     */
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
}
