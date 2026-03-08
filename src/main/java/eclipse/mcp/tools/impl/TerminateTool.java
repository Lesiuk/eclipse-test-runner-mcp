package eclipse.mcp.tools.impl;

import eclipse.mcp.tools.Args;
import eclipse.mcp.tools.IMcpTool;
import eclipse.mcp.tools.InputSchema;
import eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;

import java.util.Map;

public class TerminateTool implements IMcpTool {

    @Override
    public String getName() {
        return "terminate";
    }

    @Override
    public String getDescription() {
        return "Terminate running launches. If 'name' is provided, only terminates launches matching that configuration name. Otherwise terminates all.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("name", PropertySchema.string("Optional: name of the launch configuration to terminate. If omitted, terminates all."))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String configName = args.getString("name");
        int count = 0;

        for (ILaunch launch : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
            if (launch.isTerminated()) continue;
            if (configName != null && !matchesConfig(launch, configName)) continue;

            launch.terminate();
            count++;
        }

        return Map.of("terminated", count);
    }

    private boolean matchesConfig(ILaunch launch, String name) {
        return launch.getLaunchConfiguration() != null
                && name.equals(launch.getLaunchConfiguration().getName());
    }
}
