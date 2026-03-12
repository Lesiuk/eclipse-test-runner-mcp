package uk.l3si.eclipse.mcp.tools;

public interface McpTool {

    String getName();

    String getDescription();

    InputSchema getInputSchema();

    Object execute(Args args) throws Exception;
}
