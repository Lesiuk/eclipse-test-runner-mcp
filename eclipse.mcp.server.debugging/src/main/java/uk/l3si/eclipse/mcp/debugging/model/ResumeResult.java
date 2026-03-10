package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class ResumeResult {
    private boolean resumed;
    private String thread;
}
