package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ProblemsResult {
    private int errorCount;
    private int warningCount;
    private List<GroupedProblem> errors;
    private List<GroupedProblem> warnings;
}
