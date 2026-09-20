package uk.l3si.eclipse.mcp.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Builder(builderClassName = "Builder")
@Getter
public class TestRunResult {
    private String status;
    private int totalTests;
    private int passed;
    private int failed;
    private int errors;
    private int ignored;
    private Double elapsedSeconds;
    private List<TestFailureInfo> failures;
}
