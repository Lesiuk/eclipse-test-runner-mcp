package uk.l3si.eclipse.mcp.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

/**
 * Validates that Java types and methods exist in the Eclipse workspace.
 * Returns null if validation passes or cannot be performed (e.g., in tests
 * outside Eclipse). Returns an error message string if validation fails.
 */
public class JavaTypeValidator {

    /**
     * Checks that a fully qualified type exists in any open Java project.
     *
     * @return null if found or validation unavailable; error message if not found
     */
    public static String validateType(String fullyQualifiedName) {
        try {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (IProject project : projects) {
                if (!project.isOpen() || !project.hasNature(JavaCore.NATURE_ID)) continue;
                IJavaProject javaProject = JavaCore.create(project);
                IType type = javaProject.findType(fullyQualifiedName);
                if (type != null && type.exists()) return null;
            }
            return "Type not found in workspace: '" + fullyQualifiedName
                    + "'. Verify the fully qualified name is correct.";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks that a method exists on a fully qualified type in any open Java project.
     *
     * @return null if found or validation unavailable; error message if not found
     */
    public static String validateMethod(String fullyQualifiedName, String methodName) {
        try {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for (IProject project : projects) {
                if (!project.isOpen() || !project.hasNature(JavaCore.NATURE_ID)) continue;
                IJavaProject javaProject = JavaCore.create(project);
                IType type = javaProject.findType(fullyQualifiedName);
                if (type != null && type.exists()) {
                    for (IMethod method : type.getMethods()) {
                        if (methodName.equals(method.getElementName())) return null;
                    }
                    return "Method '" + methodName + "' not found on '"
                            + fullyQualifiedName
                            + "'. Check the method name after the underscore.";
                }
            }
            return "Interface not found in workspace: '" + fullyQualifiedName
                    + "'. Verify the fully qualified name is correct.";
        } catch (Exception e) {
            return null;
        }
    }
}
