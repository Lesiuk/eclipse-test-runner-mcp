package uk.l3si.eclipse.mcp.model;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ProblemInfo {
    private String project;
    private String file;
    private Integer line;
    private String message;
}
