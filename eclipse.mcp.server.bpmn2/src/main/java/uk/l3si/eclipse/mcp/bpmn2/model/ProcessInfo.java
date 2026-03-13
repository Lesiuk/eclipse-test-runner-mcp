package uk.l3si.eclipse.mcp.bpmn2.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ProcessInfo {
    private String processId;
    private String processName;
    private String packageName;
    private List<VariableInfo> variables;
    private List<SignalInfo> signals;
    private List<NodeInfo> nodes;
    private List<FlowInfo> flows;
}
