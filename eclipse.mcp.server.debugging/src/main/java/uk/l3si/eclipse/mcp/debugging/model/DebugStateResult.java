package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class DebugStateResult {
    private boolean active;
    private Boolean suspended;
    private String thread;
    private Long threadId;
    private LocationInfo location;
    private String reason;
}
