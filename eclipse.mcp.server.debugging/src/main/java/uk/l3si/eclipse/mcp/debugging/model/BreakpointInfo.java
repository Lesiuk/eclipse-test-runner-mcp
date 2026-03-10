package uk.l3si.eclipse.mcp.debugging.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder
public class BreakpointInfo {
    private long id;
    @SerializedName("class")
    private String className;
    private int line;
    private String condition;
    private Boolean enabled;
    private Integer hitCount;
    private String error;
}
