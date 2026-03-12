package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.ListTestConfigsResult;
import uk.l3si.eclipse.mcp.model.TestConfigInfo;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;

import java.util.Arrays;
import java.util.List;

public class ListLaunchConfigsTool implements McpTool {

    @Override
    public String getName() {
        return "list_test_configs";
    }

    @Override
    public String getDescription() {
        return "List all JUnit launch configurations in Eclipse (both regular JUnit and JUnit Plug-in Test). "
             + "Returns name and type for each. Use these config names with 'run_test'.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder().build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        ILaunchConfiguration[] allConfigs = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations();

        List<TestConfigInfo> junitConfigs = Arrays.stream(allConfigs)
                .filter(TestLaunchHelper::isJUnitConfig)
                .map(this::toConfigInfo)
                .toList();

        return ListTestConfigsResult.builder().testConfigurations(junitConfigs).build();
    }

    private TestConfigInfo toConfigInfo(ILaunchConfiguration config) {
        try {
            return TestConfigInfo.builder()
                    .name(config.getName())
                    .type(config.getType().getName())
                    .build();
        } catch (Exception ex) {
            return TestConfigInfo.builder()
                    .name(config.getName())
                    .type("unknown")
                    .build();
        }
    }
}
