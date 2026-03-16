package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class ResumeResult {
    private String thread;
    private boolean stopped;
    private String reason;
    private LocationInfo location;
}
