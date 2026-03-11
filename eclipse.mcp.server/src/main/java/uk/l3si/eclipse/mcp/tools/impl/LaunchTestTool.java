package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;

import java.util.List;

public class LaunchTestTool implements IMcpTool {

    @Override
    public String getName() {
        return "launch_test";
    }

    @Override
    public String getDescription() {
        return "Launch a JUnit test without refreshing or rebuilding the project. "
             + "Use this to re-run tests quickly when code has NOT changed since the last build. "
             + "If you edited files externally, use 'run_test' instead — it refreshes and rebuilds before running. "
             + "Requires an existing JUnit launch configuration (regular JUnit or JUnit Plug-in Test) which provides "
             + "all runtime settings (VM args, classpath, environment). Overrides the test target to run the specified class/method. "
             + "Fails if a test is already running — use 'terminate' to stop it first. "
             + "Waits for completion and returns test results.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("config", PropertySchema.string("Name of an existing JUnit launch configuration to use as template"))
                .property("class", PropertySchema.string("Fully qualified test class name (e.g. 'com.example.FooTest')"))
                .property("method", PropertySchema.string("Optional: specific test method name to run. If omitted, runs all tests in the class."))
                .property("project", PropertySchema.string("Project containing the test class. Overrides the project in the launch config. If omitted, uses the project from the existing config."))
                .required(List.of("config", "class"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String configName = args.requireString("config", "launch configuration name");
        String className = args.requireString("class", "fully qualified test class name");
        String methodName = args.getString("method");
        String projectName = args.getString("project");

        // Fail fast if breakpoints exist — launch_test runs in 'run' mode only
        IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints();
        if (breakpoints.length > 0) {
            throw new IllegalStateException(
                    "launch_test runs in 'run' mode — breakpoints will not be hit. "
                    + "Use 'run_test' with mode='debug' to debug tests with breakpoints.");
        }

        TestLaunchHelper.checkNoTestRunning();
        return TestLaunchHelper.launchTest(configName, className, methodName, projectName, "run");
    }
}
