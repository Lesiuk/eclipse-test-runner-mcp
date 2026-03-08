package eclipse.mcp.tools;

import lombok.Builder;

@Builder
public class PropertySchema {
    private String type;
    private String description;
    private PropertySchema items;

    public static PropertySchema string(String description) {
        return PropertySchema.builder().type("string").description(description).build();
    }

    public static PropertySchema bool(String description) {
        return PropertySchema.builder().type("boolean").description(description).build();
    }

    public static PropertySchema array(String description, PropertySchema items) {
        return PropertySchema.builder().type("array").description(description).items(items).build();
    }
}
