package uk.l3si.eclipse.mcp.debugging.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder
public class BreakpointResult {
    private long id;
    @SerializedName("class")
    private String className;
    private int line;
    private String condition;
    private boolean enabled;
    private Integer adjustedFrom;
}
