package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ExpressionResult {
    private String type;
    private Object value;
    private Integer length;
    private List<String> fields;
    private List<ArrayElementInfo> elements;
    private Boolean truncated;
}
