package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ListVariablesResult {
    private String frame;
    private int variableCount;
    private List<VariableResult> variables;
}
