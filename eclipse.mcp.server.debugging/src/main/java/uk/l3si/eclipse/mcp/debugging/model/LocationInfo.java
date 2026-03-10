package uk.l3si.eclipse.mcp.debugging.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder
public class LocationInfo {
    @SerializedName("class")
    private String className;
    private String method;
    private int line;
    private String sourceName;
}
