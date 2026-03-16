package uk.l3si.eclipse.mcp.model;

import lombok.Builder;

@Builder
public class LineCoverageInfo {
    private int line;
    private String status;
    private String branches;
}
