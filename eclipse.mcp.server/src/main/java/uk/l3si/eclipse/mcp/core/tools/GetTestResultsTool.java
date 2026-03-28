package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.Map;

public class GetTestResultsTool implements McpTool {

    @Override
    public String getName() {
        return "get_test_results";
    }

    @Override
    public String getDescription() {
        return "Get results of the most recent JUnit test run. "
             + "Returns test counts, pass/fail status, and condensed failure messages. "
             + "To get the full unabridged stack trace for a specific failure, pass class and method. "
             + "Pass wait=true to wait for a running test to complete first.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("wait", PropertySchema.bool("Wait for a running test to complete before returning results. Default: false"))
                .property("class", PropertySchema.string("Fully qualified test class name (e.g. 'com.example.FooTest')"))
                .property("method", PropertySchema.string("Test method name — when specified with class, returns full stack trace for that failure"))
                .build();
    }

    @Override
    public Object execute(Args args, ProgressReporter progress) throws Exception {
        checkNoActiveDebugLaunch();

        String className = args.getString("class");
        String methodName = args.getString("method");

        if (methodName != null && className == null) {
            throw new IllegalArgumentException(
                    "'class' is required when 'method' is specified.");
        }

        // Full trace mode: return the complete stack trace for a specific failure
        if (methodName != null) {
            String trace = TestResultsHelper.getFailureTrace(className, methodName);
            if (trace == null) {
                throw new IllegalArgumentException(
                        "No failure trace found for " + className + "#" + methodName
                        + ". Make sure a test run has completed and this test actually failed.");
            }
            return Map.of("trace", trace);
        }

        // Summary mode: return test run results
        boolean wait = args.getBoolean("wait");
        TestRunResult result = TestResultsHelper.collect(wait, progress);
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
