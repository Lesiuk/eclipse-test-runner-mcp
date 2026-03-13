package uk.l3si.eclipse.mcp.bpmn2.model;

import lombok.Builder;

@Builder
public class AddNodeResult {
    private String id;
    private String type;
    private String name;
}
