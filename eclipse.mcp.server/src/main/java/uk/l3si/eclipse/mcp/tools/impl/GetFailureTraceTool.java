package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;

import java.util.List;
import java.util.Map;

public class GetFailureTraceTool implements IMcpTool {

    @Override
    public String getName() {
        return "get_failure_trace";
    }

    @Override
    public String getDescription() {
        return "Get the full stack trace for a specific test failure. "
             + "Use this after run_test or get_test_results shows a failure and you need the complete stack trace to diagnose it. "
             + "Pass the fully qualified class name and test method name from the failure.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("class", PropertySchema.string("Fully qualified test class name (e.g. 'com.example.FooTest')"))
                .property("method", PropertySchema.string("Test method name (e.g. 'testSomething')"))
                .required(List.of("class", "method"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String className = args.requireString("class", "fully qualified test class name");
        String methodName = args.requireString("method", "test method name");

        String trace = TestResultsHelper.getFailureTrace(className, methodName);
        if (trace == null) {
            throw new IllegalArgumentException(
                    "No failure trace found for " + className + "#" + methodName
                    + ". Make sure a test run has completed and this test actually failed.");
        }
        return Map.of(
                "class", className,
                "method", methodName,
                "trace", trace
        );
    }
}
