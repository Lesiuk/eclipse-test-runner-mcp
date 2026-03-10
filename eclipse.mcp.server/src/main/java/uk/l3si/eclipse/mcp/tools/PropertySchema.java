package uk.l3si.eclipse.mcp.tools;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;

import java.util.List;

@Builder
public class PropertySchema {
    private String type;
    private String description;
    private PropertySchema items;
    @SerializedName("enum")
    private List<String> enumValues;

    public static PropertySchema string(String description) {
        return PropertySchema.builder().type("string").description(description).build();
    }

    public static PropertySchema bool(String description) {
        return PropertySchema.builder().type("boolean").description(description).build();
    }

    public static PropertySchema array(String description, PropertySchema items) {
        return PropertySchema.builder().type("array").description(description).items(items).build();
    }

    public static PropertySchema stringEnum(String description, List<String> enumValues) {
        return PropertySchema.builder().type("string").description(description).enumValues(enumValues).build();
    }
}
