package uk.l3si.eclipse.mcp.tools;

import lombok.Builder;
import lombok.Singular;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder
public class InputSchema {
    @Builder.Default
    private String type = "object";
    @Singular private Map<String, PropertySchema> properties;
    private List<String> required;

    public Set<String> getPropertyNames() {
        return properties != null ? properties.keySet() : Collections.emptySet();
    }
}
