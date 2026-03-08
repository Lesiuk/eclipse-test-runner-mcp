package eclipse.mcp.model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

@Builder(builderClassName = "Builder")
public class TestFailureInfo {
    @SerializedName("class")
    private String className;
    private String method;
    private String kind;
    private String message;
    private String expected;
    private String actual;
}
