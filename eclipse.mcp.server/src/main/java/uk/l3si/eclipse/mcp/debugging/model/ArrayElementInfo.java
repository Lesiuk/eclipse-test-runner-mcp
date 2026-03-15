package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class ArrayElementInfo {
    private int index;
    private String type;
    private String value;
}
