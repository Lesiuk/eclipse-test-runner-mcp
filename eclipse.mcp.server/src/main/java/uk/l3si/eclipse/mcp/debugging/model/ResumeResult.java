package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;
import uk.l3si.eclipse.mcp.model.TestRunResult;

@Builder
public class ResumeResult {
    private String thread;
    private boolean stopped;
    private String reason;
    private LocationInfo location;
    private TestRunResult testResults;
}
