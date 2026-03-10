package uk.l3si.eclipse.mcp.model;

import lombok.Builder;

@Builder
public class TestRunInfo {
    private String name;
    private String mode;
    private boolean terminated;
}
