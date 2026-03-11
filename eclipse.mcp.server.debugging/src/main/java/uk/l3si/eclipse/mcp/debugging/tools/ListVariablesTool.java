package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ArrayElementInfo;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ListVariablesTool implements IMcpTool {

    private static final int MAX_ARRAY_PREVIEW = 5;

    private final DebugContext debugContext;

    public ListVariablesTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "list_variables";
    }

    @Override
    public String getDescription() {
        return "List all visible variables in the current stack frame with their types and values. "
             + "Shows local variables, method parameters, and 'this' fields. "
             + "For objects, shows field names. For arrays, shows length and first few elements. "
             + "Use 'inspect_variable' to drill deeper into specific variables.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("thread_id", PropertySchema.builder().type("integer").description("Thread ID. Defaults to current suspended thread.").build())
                .property("frame_index", PropertySchema.builder().type("integer").description("Stack frame index (0 = top). Defaults to 0.").build())
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        Long threadId = args.getLong("thread_id");
        Integer frameIndex = args.getInt("frame_index");

        IJavaThread thread = debugContext.resolveThread(threadId);
        IJavaStackFrame frame = debugContext.resolveFrame(thread, frameIndex);

        List<VariableResult> variables = new ArrayList<>();
        for (IVariable v : frame.getVariables()) {
            if (v.getValue() instanceof IJavaValue javaValue) {
                variables.add(formatValue(v.getName(), javaValue));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frame", frame.getDeclaringTypeName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
        result.put("variables", variables);
        return result;
    }

    private VariableResult formatValue(String name, IJavaValue value) throws DebugException {
        VariableResult.VariableResultBuilder builder = VariableResult.builder()
                .name(name)
                .type(value.getReferenceTypeName());

        if (value.isNull()) {
            builder.value("null");
        } else if (value instanceof IJavaArray array) {
            int length = array.getLength();
            builder.value(value.getReferenceTypeName() + "[" + length + "]")
                    .length(length);

            int preview = Math.min(length, MAX_ARRAY_PREVIEW);
            List<ArrayElementInfo> elements = new ArrayList<>();
            for (int i = 0; i < preview; i++) {
                IJavaValue elem = array.getValue(i);
                elements.add(ArrayElementInfo.builder()
                        .index(i)
                        .type(elem.getReferenceTypeName())
                        .value(elem.getValueString())
                        .build());
            }
            builder.elements(elements);
            if (length > preview) {
                builder.truncated(true);
            }
        } else if (value instanceof IJavaObject) {
            builder.value(value.getValueString());
            List<String> fieldNames = new ArrayList<>();
            for (IVariable v : value.getVariables()) {
                fieldNames.add(v.getName());
            }
            if (!fieldNames.isEmpty()) {
                builder.fields(fieldNames);
            }
        } else {
            builder.value(value.getValueString());
        }

        return builder.build();
    }
}
