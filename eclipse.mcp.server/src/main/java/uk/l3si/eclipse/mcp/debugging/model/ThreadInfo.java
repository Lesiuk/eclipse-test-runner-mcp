package uk.l3si.eclipse.mcp.debugging.model;

import lombok.Builder;

@Builder
public class ThreadInfo {
    private String name;
    private Long id;
    private String state;
    private String location;
    private String error;
}
