package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class MethodCoverageInfo {
    private String name;
    private String lineCoverage;
    private String branchCoverage;
    private List<Integer> uncoveredLines;
}
