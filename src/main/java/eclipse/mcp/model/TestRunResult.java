package eclipse.mcp.model;

import java.util.List;
import lombok.Builder;

@Builder(builderClassName = "Builder")
public class TestRunResult {
    private String testRunName;
    private String configName;
    private String status;
    private int totalTests;
    private int passed;
    private int failed;
    private int errors;
    private int ignored;
    private Double elapsedSeconds;
    private List<TestFailureInfo> failures;
}
