package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class StepResult {
    private String action;
    private String thread;
    private Boolean terminated;
    private String reason;
    private LocationInfo location;
    private String error;
}
