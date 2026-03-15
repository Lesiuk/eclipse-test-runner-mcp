package uk.l3si.eclipse.mcp.bpmn2.model;

import lombok.Builder;

@Builder
public class NodeInfo {
    private String id;
    private String type;
    private String name;
    private String taskName;
    private String script;
    private String calledElement;
    private String direction;
    private String signalRef;
}
