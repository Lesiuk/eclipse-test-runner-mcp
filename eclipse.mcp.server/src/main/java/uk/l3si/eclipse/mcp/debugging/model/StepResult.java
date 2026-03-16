package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;
import uk.l3si.eclipse.mcp.model.TestRunResult;

@Builder
public class StepResult {
    private String action;
    private String thread;
    private Boolean terminated;
    private String reason;
    private LocationInfo location;
    private String error;
    private TestRunResult testResults;
}
