package eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ProblemsResult {
    private int errorCount;
    private int warningCount;
    private List<ProblemInfo> errors;
    private List<ProblemInfo> warnings;
}
