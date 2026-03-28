package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class CoverageResult {
    private CoverageSummary summary;
    private List<MethodCoverageInfo> methods;
    private List<LineCoverageInfo> lines;
}
