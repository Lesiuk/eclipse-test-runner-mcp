package uk.l3si.eclipse.mcp.model;

import lombok.Builder;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;

import java.util.List;

@Builder(builderClassName = "Builder")
public class LaunchTestResult {
    private TestRunResult testResults;
    private String testResultsError;
    private Boolean debugStopped;
    private String debugReason;
    private LocationInfo debugLocation;
    private List<VariableResult> debugVariables;
    private String hint;
}
