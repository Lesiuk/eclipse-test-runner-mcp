package eclipse.mcp.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder(builderClassName = "Builder")
public class LaunchTestResult {
    private String config;
    private String project;
    @SerializedName("class")
    private String className;
    private String method;
    private TestRunResult testResults;
    private String testResultsError;
}
