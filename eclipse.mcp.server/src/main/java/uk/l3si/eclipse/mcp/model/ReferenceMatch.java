package uk.l3si.eclipse.mcp.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder
public class ReferenceMatch {
    private int line;
    private String source;
}
