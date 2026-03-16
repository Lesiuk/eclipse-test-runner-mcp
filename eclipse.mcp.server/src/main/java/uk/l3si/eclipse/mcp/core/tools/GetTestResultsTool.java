package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

public class GetTestResultsTool implements McpTool {

    @Override
    public String getName() {
        return "get_test_results";
    }

    @Override
    public String getDescription() {
        return "Get results of the most recent JUnit test run without re-running tests. "
             + "Returns test counts, pass/fail status, and failure details including stack traces. "
             + "Use this to re-check results of a previous run. "
             + "Pass wait=true to wait for a currently running test to complete first. "
             + "Cannot be used while a debug session is active — resume or terminate the debug session first.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("wait", PropertySchema.bool("Wait for a running test to complete before returning results. Default: false"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        checkNoActiveDebugLaunch();

        boolean wait = args.getBoolean("wait");

        TestRunResult result = TestResultsHelper.collect(wait);
        if (result == null) {
            throw new IllegalStateException("No test runs found. Run a JUnit launch configuration first.");
        }
        return result;
    }

    private void checkNoActiveDebugLaunch() {
        try {
            ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
            for (ILaunch launch : manager.getLaunches()) {
                if (!launch.isTerminated()
                        && ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode())
                        && launch.getLaunchConfiguration() != null
                        && TestLaunchHelper.isJUnitConfig(launch.getLaunchConfiguration())) {
                    throw new IllegalStateException(
                            "Cannot retrieve test results while a debug session is active. "
                            + "The test is paused at a breakpoint and will never complete. "
                            + "Resume or terminate the debug session first.");
                }
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // best effort — don't block if we can't determine debug state
        }
    }
}
