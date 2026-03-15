package uk.l3si.eclipse.mcp.bpmn2.model;

import lombok.Builder;

@Builder
public class RemoveVariableResult {
    private String name;
    private boolean removed;
}
