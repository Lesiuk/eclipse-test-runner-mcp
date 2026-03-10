package eclipse.mcp.tools.impl;

import org.eclipse.eclemma.core.CoverageTools;
import org.eclipse.eclemma.core.analysis.IJavaCoverageListener;
import org.eclipse.eclemma.core.analysis.IJavaModelCoverage;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ICoverageNode;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.ISourceNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("restriction")
class CoverageHelper {

    private static final long COVERAGE_ANALYSIS_TIMEOUT_MS = 30_000;

    /**
     * Wait for EclEmma's coverage analysis to complete after a coverage launch.
     * Listens for the coverageChanged() callback, with a timeout.
     */
    static void waitForCoverageAnalysis() throws InterruptedException {
        IJavaModelCoverage existing = CoverageTools.getJavaModelCoverage();
        if (existing != null && existing != IJavaModelCoverage.LOADING) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        IJavaCoverageListener listener = latch::countDown;

        CoverageTools.addJavaCoverageListener(listener);
        try {
            // Check again after registering listener to avoid race
            existing = CoverageTools.getJavaModelCoverage();
            if (existing != null && existing != IJavaModelCoverage.LOADING) {
                return;
            }
            latch.await(COVERAGE_ANALYSIS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } finally {
            CoverageTools.removeJavaCoverageListener(listener);
        }
    }

    /**
     * Get detailed coverage for a specific class.
     * Returns a map structure with summary, methods, and per-line detail.
     */
    static Map<String, Object> getCoverageForClass(String className) throws Exception {
        IJavaModelCoverage modelCoverage = CoverageTools.getJavaModelCoverage();
        if (modelCoverage == null) {
            throw new IllegalStateException(
                    "No coverage data available. Run a test with coverage=true first.");
        }
        if (modelCoverage == IJavaModelCoverage.LOADING) {
            waitForCoverageAnalysis();
            modelCoverage = CoverageTools.getJavaModelCoverage();
            if (modelCoverage == null || modelCoverage == IJavaModelCoverage.LOADING) {
                throw new IllegalStateException(
                        "Coverage analysis did not complete in time. Try again.");
            }
        }

        // Find the IType across all projects in the coverage model
        IType type = findType(modelCoverage, className);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Class '" + className + "' not found in any project in the coverage scope. "
                    + "Make sure the class name is fully qualified and the class is in the coverage scope.");
        }

        ICoverageNode classCoverage = modelCoverage.getCoverageFor(type);
        if (classCoverage == null) {
            throw new IllegalArgumentException(
                    "No coverage data for class '" + className + "'. "
                    + "The class may not be in the coverage scope, or no code paths in this class were exercised.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("class", className);
        result.put("summary", buildSummary(classCoverage));
        result.put("methods", buildMethods(classCoverage, type));
        result.put("lines", buildLines(classCoverage));
        return result;
    }

    private static IType findType(IJavaModelCoverage modelCoverage, String className) throws Exception {
        for (IJavaProject javaProject : modelCoverage.getProjects()) {
            IType type = javaProject.findType(className);
            if (type != null && type.exists()) {
                return type;
            }
        }
        return null;
    }

    private static Map<String, String> buildSummary(ICoverageNode node) {
        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("lineCoverage", formatCounter(node.getLineCounter()));
        summary.put("branchCoverage", formatCounter(node.getBranchCounter()));
        summary.put("methodCoverage", formatCounter(node.getMethodCounter()));
        return summary;
    }

    private static List<Map<String, Object>> buildMethods(ICoverageNode classCoverage, IType type) {
        List<Map<String, Object>> methods = new ArrayList<>();

        // Walk via the IType's methods and query coverage for each
        try {
            for (IMethod method : type.getMethods()) {
                ICoverageNode methodCoverage = CoverageTools.getCoverageInfo(method);
                if (methodCoverage == null) continue;

                Map<String, Object> methodMap = new LinkedHashMap<>();
                methodMap.put("name", method.getElementName());
                methodMap.put("lineCoverage", formatCounter(methodCoverage.getLineCounter()));
                methodMap.put("branchCoverage", formatCounter(methodCoverage.getBranchCounter()));

                // Collect uncovered lines for this method
                if (methodCoverage instanceof ISourceNode methodSource) {
                    List<Integer> uncovered = new ArrayList<>();
                    int first = methodSource.getFirstLine();
                    int last = methodSource.getLastLine();
                    if (first != ISourceNode.UNKNOWN_LINE) {
                        for (int line = first; line <= last; line++) {
                            ILine lineInfo = methodSource.getLine(line);
                            int status = lineInfo.getStatus();
                            if (status == ICounter.NOT_COVERED || status == ICounter.PARTLY_COVERED) {
                                uncovered.add(line);
                            }
                        }
                    }
                    if (!uncovered.isEmpty()) {
                        methodMap.put("uncoveredLines", uncovered);
                    }
                }

                methods.add(methodMap);
            }
        } catch (Exception e) {
            // If we can't enumerate methods, return what we have
        }

        return methods;
    }

    private static List<Map<String, Object>> buildLines(ICoverageNode classCoverage) {
        List<Map<String, Object>> lines = new ArrayList<>();

        if (!(classCoverage instanceof ISourceNode sourceNode)) {
            return lines;
        }

        int first = sourceNode.getFirstLine();
        int last = sourceNode.getLastLine();
        if (first == ISourceNode.UNKNOWN_LINE) {
            return lines;
        }

        for (int lineNum = first; lineNum <= last; lineNum++) {
            ILine line = sourceNode.getLine(lineNum);
            int status = line.getStatus();
            if (status == ICounter.EMPTY) continue;

            Map<String, Object> lineMap = new LinkedHashMap<>();
            lineMap.put("line", lineNum);
            lineMap.put("status", statusToString(status));

            ICounter branchCounter = line.getBranchCounter();
            if (branchCounter.getTotalCount() > 0) {
                lineMap.put("branches", branchCounter.getCoveredCount() + "/" + branchCounter.getTotalCount());
            }

            lines.add(lineMap);
        }

        return lines;
    }

    private static String formatCounter(ICounter counter) {
        int covered = counter.getCoveredCount();
        int total = counter.getTotalCount();
        if (total == 0) return "0/0";
        double pct = 100.0 * covered / total;
        return covered + "/" + total + " (" + String.format("%.1f%%", pct) + ")";
    }

    private static String statusToString(int status) {
        return switch (status) {
            case ICounter.FULLY_COVERED -> "COVERED";
            case ICounter.NOT_COVERED -> "NOT_COVERED";
            case ICounter.PARTLY_COVERED -> "PARTLY_COVERED";
            default -> "EMPTY";
        };
    }
}
