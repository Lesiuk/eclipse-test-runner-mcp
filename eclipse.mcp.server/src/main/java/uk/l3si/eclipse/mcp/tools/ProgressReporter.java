package uk.l3si.eclipse.mcp.tools;

@FunctionalInterface
public interface ProgressReporter {
    void report(String message);
}
