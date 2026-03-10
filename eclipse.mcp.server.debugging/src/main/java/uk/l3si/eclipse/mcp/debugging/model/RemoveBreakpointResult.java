package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class RemoveBreakpointResult {
    private boolean removed;
    private long id;
}
