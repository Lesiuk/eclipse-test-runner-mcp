package uk.l3si.eclipse.mcp.debugging.model;

import java.util.List;
import lombok.Builder;

@Builder
public class ExpressionResult {
    private String expression;
    private String type;
    private String value;
    private Integer length;
    private List<String> fields;
}
