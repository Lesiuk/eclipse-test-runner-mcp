package eclipse.mcp.tools.impl;

import eclipse.mcp.tools.Args;
import eclipse.mcp.tools.IMcpTool;
import eclipse.mcp.tools.InputSchema;
import eclipse.mcp.tools.PropertySchema;

import java.util.List;

public class GetCoverageTool implements IMcpTool {

    @Override
    public String getName() {
        return "get_coverage";
    }

    @Override
    public String getDescription() {
        return "Get detailed code coverage for a specific source class. "
             + "Use this after running a test with coverage=true to see which lines and branches are covered. "
             + "Returns per-method and per-line coverage detail. "
             + "Pass the fully qualified class name of the SOURCE class (not the test class).";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("class", PropertySchema.string(
                        "Fully qualified source class name (e.g. 'com.example.MyService') — the class under test, not the test class"))
                .required(List.of("class"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String className = args.requireString("class", "fully qualified source class name");
        return CoverageHelper.getCoverageForClass(className);
    }
}
