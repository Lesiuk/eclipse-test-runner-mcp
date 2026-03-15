package uk.l3si.eclipse.mcp.bpmn2.model;

import java.util.List;
import lombok.Builder;

@Builder
public class RemoveNodeResult {
    private String id;
    private List<String> removedFlows;
}
