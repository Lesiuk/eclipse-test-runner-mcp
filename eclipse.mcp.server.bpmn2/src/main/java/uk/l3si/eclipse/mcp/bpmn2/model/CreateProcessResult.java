package uk.l3si.eclipse.mcp.bpmn2.model;

import lombok.Builder;

@Builder
public class CreateProcessResult {
    private String file;
    private String processId;
}
