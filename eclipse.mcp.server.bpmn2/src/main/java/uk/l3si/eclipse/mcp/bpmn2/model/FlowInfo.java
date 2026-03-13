package uk.l3si.eclipse.mcp.bpmn2.model;

import lombok.Builder;

@Builder
public class FlowInfo {
    private String id;
    private String source;
    private String target;
    private String name;
    private String condition;
    private String priority;
}
