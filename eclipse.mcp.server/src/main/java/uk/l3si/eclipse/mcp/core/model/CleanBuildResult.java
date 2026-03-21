package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class CleanBuildResult {
    private List<String> projects;
    private ProblemsResult problems;
}
