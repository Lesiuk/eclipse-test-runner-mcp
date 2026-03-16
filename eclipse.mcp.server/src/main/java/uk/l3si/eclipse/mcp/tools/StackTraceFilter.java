package uk.l3si.eclipse.mcp.tools;

/**
 * Shared utility for filtering framework frames from stack traces.
 * Used by both test-result formatting and expression-evaluation error reporting.
 */
public final class StackTraceFilter {

    private static final String[] FRAMEWORK_PREFIXES = {
            "org.junit.",
            "org.eclipse.jdt.internal.junit.",
            "org.eclipse.jdt.internal.debug.",
            "jdk.internal.",
            "sun.reflect.",
            "java.lang.reflect.",
    };

    /**
     * Check whether a class name (or a trace-line fragment after stripping
     * the {@code "at "} prefix) belongs to a test/debug framework.
     * Handles Java 9+ module prefixes like {@code java.base/java.lang.reflect.Method}.
     */
    public static boolean isFrameworkFrame(String classOrLine) {
        if (classOrLine == null) return false;
        int slash = classOrLine.indexOf('/');
        if (slash >= 0) {
            // Module-prefixed frame (e.g. "java.base/java.lang.Thread")
            String module = classOrLine.substring(0, slash);
            if (module.startsWith("java.") || module.startsWith("jdk.")) return true;
            classOrLine = classOrLine.substring(slash + 1);
        }
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (classOrLine.startsWith(prefix)) return true;
        }
        return false;
    }

    private StackTraceFilter() {}
}
