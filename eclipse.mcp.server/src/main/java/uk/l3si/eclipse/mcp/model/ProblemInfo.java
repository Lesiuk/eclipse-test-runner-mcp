package uk.l3si.eclipse.mcp.model;

import lombok.Builder;

@Builder
public class ProblemInfo {
    private String project;
    private String file;
    private Integer line;
    private String message;
}
