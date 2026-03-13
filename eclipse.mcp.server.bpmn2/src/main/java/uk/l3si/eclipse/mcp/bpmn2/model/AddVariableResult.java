package uk.l3si.eclipse.mcp.bpmn2.model;

import lombok.Builder;

@Builder
public class AddVariableResult {
    private String name;
    private String type;
    private String itemDefinitionId;
}
