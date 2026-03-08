package eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class RunTestResult {
    private List<String> steps;
    private boolean success;
    private Integer errorCount;
    private List<ProblemInfo> compilationErrors;
    private LaunchTestResult launchResult;
}
