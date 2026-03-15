package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.TerminateResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;

import java.util.ArrayList;
import java.util.List;

public class TerminateTool implements McpTool {

    @Override
    public String getName() {
        return "terminate";
    }

    @Override
    public String getDescription() {
        return "Terminate running launches. Use when a test is stuck, when run_test reports 'test already running', "
             + "or when you need to stop a debug session. "
             + "If 'name' is provided, only terminates launches matching that configuration name. Otherwise terminates all.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("name", PropertySchema.string("Optional: name of the launch configuration to terminate. If omitted, terminates all."))
                .build();
    }

    private static final long TERMINATION_TIMEOUT_MS = 10_000;
    private static final long POLL_INTERVAL_MS = 100;

    @Override
    public Object execute(Args args) throws Exception {
        String configName = args.getString("name");
        List<ILaunch> toTerminate = new ArrayList<>();

        for (ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
            if (launch.isTerminated()) continue;
            if (configName != null && !matchesConfig(launch, configName)) continue;

            launch.terminate();
            toTerminate.add(launch);
        }

        if (toTerminate.isEmpty() && configName != null) {
            throw new IllegalArgumentException(
                    "No running launch found with name '" + configName + "'. "
                    + "Use 'terminate' without 'name' to terminate all, or check the launch configuration name.");
        }

        // Wait for all launches to fully terminate
        long deadline = System.currentTimeMillis() + TERMINATION_TIMEOUT_MS;
        for (ILaunch launch : toTerminate) {
            while (!launch.isTerminated() && System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_MS);
            }
            if (!launch.isTerminated()) {
                throw new IllegalStateException(
                        "Launch '" + (launch.getLaunchConfiguration() != null ? launch.getLaunchConfiguration().getName() : "unknown")
                        + "' did not terminate within " + (TERMINATION_TIMEOUT_MS / 1000) + "s");
            }
        }

        return TerminateResult.builder().terminated(toTerminate.size()).build();
    }

    private boolean matchesConfig(ILaunch launch, String name) {
        return launch.getLaunchConfiguration() != null
                && name.equals(launch.getLaunchConfiguration().getName());
    }
}
