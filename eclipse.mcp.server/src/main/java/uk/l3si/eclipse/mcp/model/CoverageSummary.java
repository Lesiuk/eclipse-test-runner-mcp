package uk.l3si.eclipse.mcp.model;

import lombok.Builder;

@Builder
public class CoverageSummary {
    private String lineCoverage;
    private String branchCoverage;
    private String methodCoverage;
}
