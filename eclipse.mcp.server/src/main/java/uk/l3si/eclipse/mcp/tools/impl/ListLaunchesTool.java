package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.ListTestRunsResult;
import uk.l3si.eclipse.mcp.model.TestRunInfo;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;

import java.util.Arrays;
import java.util.List;

public class ListLaunchesTool implements IMcpTool {

    @Override
    public String getName() {
        return "list_test_runs";
    }

    @Override
    public String getDescription() {
        return "List active and recent JUnit test runs with their status (running or terminated). "
             + "Use this to check if tests are still running or have completed.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder().build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        ILaunch[] allLaunches = DebugPlugin.getDefault().getLaunchManager().getLaunches();

        List<TestRunInfo> testRuns = Arrays.stream(allLaunches)
                .filter(l -> l.getLaunchConfiguration() != null)
                .filter(l -> TestLaunchHelper.isJUnitConfig(l.getLaunchConfiguration()))
                .map(this::toRunInfo)
                .toList();

        return ListTestRunsResult.builder().testRuns(testRuns).build();
    }

    private TestRunInfo toRunInfo(ILaunch launch) {
        return TestRunInfo.builder()
                .name(launch.getLaunchConfiguration().getName())
                .mode(launch.getLaunchMode())
                .terminated(launch.isTerminated())
                .build();
    }
}
