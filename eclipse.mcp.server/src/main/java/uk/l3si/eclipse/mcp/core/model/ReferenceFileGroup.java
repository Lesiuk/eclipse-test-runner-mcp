package uk.l3si.eclipse.mcp.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

import java.util.List;

@Builder
public class ReferenceFileGroup {
    @SerializedName("class")
    private String className;
    private List<ReferenceMatch> references;
}
