package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.model.CoverageResult;
import uk.l3si.eclipse.mcp.model.CoverageSummary;
import uk.l3si.eclipse.mcp.model.LineCoverageInfo;
import uk.l3si.eclipse.mcp.model.MethodCoverageInfo;
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
import java.util.List;
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
    static CoverageResult getCoverageForClass(String className) throws Exception {
        IJavaModelCoverage modelCoverage = CoverageTools.getJavaModelCoverage();
        if (modelCoverage == null) {
            throw new IllegalStateException(
                    "No coverage data available. Run a test with mode='coverage' first.");
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

        return CoverageResult.builder()
                .summary(buildSummary(classCoverage))
                .methods(buildMethods(type))
                .lines(buildLines(classCoverage))
                .build();
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

    private static CoverageSummary buildSummary(ICoverageNode node) {
        return CoverageSummary.builder()
                .lineCoverage(formatCounter(node.getLineCounter()))
                .branchCoverage(formatCounter(node.getBranchCounter()))
                .methodCoverage(formatCounter(node.getMethodCounter()))
                .build();
    }

    private static List<MethodCoverageInfo> buildMethods(IType type) {
        List<MethodCoverageInfo> methods = new ArrayList<>();

        try {
            for (IMethod method : type.getMethods()) {
                ICoverageNode methodCoverage = CoverageTools.getCoverageInfo(method);
                if (methodCoverage == null) continue;

                MethodCoverageInfo.MethodCoverageInfoBuilder builder = MethodCoverageInfo.builder()
                        .name(method.getElementName())
                        .lineCoverage(formatCounter(methodCoverage.getLineCounter()))
                        .branchCoverage(formatCounter(methodCoverage.getBranchCounter()));

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
                        builder.uncoveredLines(uncovered);
                    }
                }

                methods.add(builder.build());
            }
        } catch (Exception e) {
            // If we can't enumerate methods, return what we have
        }

        return methods;
    }

    private static List<LineCoverageInfo> buildLines(ICoverageNode classCoverage) {
        List<LineCoverageInfo> lines = new ArrayList<>();

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

            LineCoverageInfo.LineCoverageInfoBuilder builder = LineCoverageInfo.builder()
                    .line(lineNum)
                    .status(statusToString(status));

            ICounter branchCounter = line.getBranchCounter();
            if (branchCounter.getTotalCount() > 0) {
                builder.branches(branchCounter.getCoveredCount() + "/" + branchCounter.getTotalCount());
            }

            lines.add(builder.build());
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
