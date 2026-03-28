package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class BreakpointResult {
    private long id;
    private int line;
    private boolean enabled;
    private Integer adjustedFrom;
}
