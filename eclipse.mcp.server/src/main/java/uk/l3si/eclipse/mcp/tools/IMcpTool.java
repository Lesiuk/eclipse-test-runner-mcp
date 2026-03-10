package uk.l3si.eclipse.mcp.tools;

public interface IMcpTool {

    String getName();

    String getDescription();

    InputSchema getInputSchema();

    Object execute(Args args) throws Exception;
}
