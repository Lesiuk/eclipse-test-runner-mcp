package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ListBreakpointsResult {
    private List<BreakpointInfo> breakpoints;
}
