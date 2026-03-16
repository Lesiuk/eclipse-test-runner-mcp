package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class VariableResult {
    private String name;
    private String type;
    private Object value;
    private Integer length;
    private List<String> fields;
    private Boolean truncated;
}
