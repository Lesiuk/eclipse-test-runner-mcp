package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class RunTestResult {
    private boolean refreshed;
    private boolean compiled;
    private List<ProblemInfo> compilationErrors;
    private LaunchTestResult launchResult;
}
