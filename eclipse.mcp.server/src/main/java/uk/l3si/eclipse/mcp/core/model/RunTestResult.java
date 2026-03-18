package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class RunTestResult {
    private List<String> refreshedAndBuilt;
    private String compilationErrorSummary;
    private List<GroupedProblem> compilationErrors;
    private LaunchTestResult launchResult;
}
