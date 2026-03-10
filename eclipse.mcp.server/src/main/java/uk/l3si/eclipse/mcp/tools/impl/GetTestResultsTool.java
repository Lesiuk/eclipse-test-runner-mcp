package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

public class GetTestResultsTool implements IMcpTool {

    @Override
    public String getName() {
        return "get_test_results";
    }

    @Override
    public String getDescription() {
        return "Get results of the most recent JUnit test run without re-running tests. "
             + "Returns test counts, pass/fail status, and failure details including stack traces. "
             + "Use this to re-check results of a previous run. "
             + "Pass wait=true to wait for a currently running test to complete first.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("wait", PropertySchema.bool("Wait for a running test to complete before returning results. Default: false"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        boolean wait = args.getBoolean("wait");

        TestRunResult result = TestResultsHelper.collect(wait);
        if (result == null) {
            throw new IllegalStateException("No test runs found. Run a JUnit launch configuration first.");
        }
        return result;
    }
}
