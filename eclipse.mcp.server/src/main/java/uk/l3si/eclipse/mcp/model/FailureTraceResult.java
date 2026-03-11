package uk.l3si.eclipse.mcp.model;

import lombok.Builder;

@Builder
public class FailureTraceResult {
    @SuppressWarnings("unused")
    private String className;
    private String method;
    private String trace;
}
