package uk.l3si.eclipse.mcp.model;

import lombok.Builder;

import java.util.List;

@Builder
public class FindReferencesResult {
    private String element;
    private int totalReferences;
    private List<ReferenceFileGroup> files;
}
