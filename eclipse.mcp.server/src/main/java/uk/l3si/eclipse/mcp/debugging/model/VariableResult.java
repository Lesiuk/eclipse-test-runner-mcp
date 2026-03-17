package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class VariableResult {
    private String name;
    private String type;
    private Object value;
    private Integer length;
    private Boolean truncated;
}
