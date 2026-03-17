package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;
import uk.l3si.eclipse.mcp.model.TestRunResult;

@Builder
public class StepResult {
    private String action;
    private String thread;
    private LocationInfo location;
    private List<VariableResult> variables;
    private Boolean terminated;
    private String reason;
    private TestRunResult testResults;
}
