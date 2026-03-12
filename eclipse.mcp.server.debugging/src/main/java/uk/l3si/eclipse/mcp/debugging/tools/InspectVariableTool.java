package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ArrayElementInfo;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InspectVariableTool implements McpTool {

    private static final Pattern ARRAY_ACCESS = Pattern.compile("(.+?)\\[(\\d+)]");
    private static final int MAX_ARRAY_PREVIEW = 10;

    private final DebugContext debugContext;

    public InspectVariableTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "inspect_variable";
    }

    @Override
    public String getDescription() {
        return "Inspect a variable in the current stack frame. Returns the variable's type and value. "
             + "For objects, returns the list of field names (call again with a path to drill deeper). "
             + "For arrays/collections, returns the size and first few elements. "
             + "Use dot notation to navigate into fields (e.g. 'myObj.field.subField'). "
             + "Use bracket notation for array access (e.g. 'list[0]' or 'matrix[2][3]').";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("name", PropertySchema.string("Variable name or dot-separated path (e.g. 'myObj', 'myObj.field', 'list[0].name')"))
                .property("thread_id", PropertySchema.builder().type("integer").description("Thread ID. Defaults to current suspended thread.").build())
                .property("frame_index", PropertySchema.builder().type("integer").description("Stack frame index (0 = top). Defaults to 0.").build())
                .required(List.of("name"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String path = args.requireString("name", "variable name or path");
        if (path.isBlank()) {
            throw new IllegalArgumentException("'name' must not be blank. Provide a variable name (e.g. 'myObj') or path (e.g. 'myObj.field').");
        }
        Long threadId = args.getLong("thread_id");
        Integer frameIndex = args.getInt("frame_index");

        IJavaThread thread = debugContext.resolveThread(threadId);
        IJavaStackFrame frame = debugContext.resolveFrame(thread, frameIndex);

        IJavaValue value = navigatePath(frame, path);
        return formatValue(path, value);
    }

    /**
     * Navigate a dot/bracket-separated path starting from a stack frame's variables.
     */
    private IJavaValue navigatePath(IJavaStackFrame frame, String path) throws Exception {
        String[] segments = splitPath(path);

        // Resolve the first segment from the frame's variables
        String firstName = segments[0];
        int arrayIndex = -1;
        Matcher m = ARRAY_ACCESS.matcher(firstName);
        if (m.matches()) {
            firstName = m.group(1);
            arrayIndex = Integer.parseInt(m.group(2));
        }

        IJavaVariable variable = findVariable(frame, firstName);
        if (variable == null) {
            throw new IllegalArgumentException("Variable not found: " + firstName);
        }

        IJavaValue current = (IJavaValue) variable.getValue();

        if (arrayIndex >= 0) {
            current = accessArray(current, arrayIndex);
        }

        // Navigate remaining segments
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            int idx = -1;
            Matcher am = ARRAY_ACCESS.matcher(segment);
            if (am.matches()) {
                segment = am.group(1);
                idx = Integer.parseInt(am.group(2));
            }

            if (segment.isEmpty() && idx >= 0) {
                // Pure array access like [0]
                current = accessArray(current, idx);
            } else {
                // Field access
                IJavaVariable field = findField(current, segment);
                if (field == null) {
                    throw new IllegalArgumentException("Field not found: " + segment);
                }
                current = (IJavaValue) field.getValue();
                if (idx >= 0) {
                    current = accessArray(current, idx);
                }
            }
        }

        return current;
    }

    private String[] splitPath(String path) {
        // Split on dots but preserve array brackets
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (char c : path.toCharArray()) {
            if (c == '[') bracketDepth++;
            if (c == ']') {
                bracketDepth--;
                if (bracketDepth < 0) {
                    throw new IllegalArgumentException("Unbalanced brackets in path: '" + path + "'.");
                }
            }
            if (c == '.' && bracketDepth == 0) {
                segments.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (bracketDepth != 0) {
            throw new IllegalArgumentException("Unbalanced brackets in path: '" + path + "'.");
        }
        segments.add(current.toString());
        return segments.toArray(new String[0]);
    }

    private IJavaVariable findVariable(IJavaStackFrame frame, String name) throws DebugException {
        // Use the built-in findVariable which searches locals and 'this' fields
        IJavaVariable found = frame.findVariable(name);
        if (found != null) {
            return found;
        }
        // Fallback: iterate variables manually
        for (IVariable v : frame.getVariables()) {
            if (v.getName().equals(name) && v instanceof IJavaVariable jv) {
                return jv;
            }
        }
        // Also check 'this' fields if we're in an instance method
        for (IVariable v : frame.getVariables()) {
            if ("this".equals(v.getName())) {
                IJavaValue thisVal = (IJavaValue) v.getValue();
                IJavaVariable field = findField(thisVal, name);
                if (field != null) return field;
            }
        }
        return null;
    }

    private IJavaVariable findField(IJavaValue value, String name) throws DebugException {
        for (IVariable v : value.getVariables()) {
            if (v.getName().equals(name) && v instanceof IJavaVariable jv) {
                return jv;
            }
        }
        return null;
    }

    private IJavaValue accessArray(IJavaValue value, int index) throws DebugException {
        if (value instanceof IJavaArray array) {
            if (index < 0 || index >= array.getLength()) {
                throw new IllegalArgumentException("Array index " + index + " out of bounds (length: " + array.getLength() + ").");
            }
            return array.getValue(index);
        }
        throw new IllegalArgumentException("Cannot use array access on non-array type: " + value.getReferenceTypeName());
    }

    /**
     * Format a value into a model class for JSON output.
     */
    private VariableResult formatValue(String name, IJavaValue value) throws DebugException {
        VariableResult.VariableResultBuilder resultBuilder = VariableResult.builder()
                .name(name)
                .type(value.getReferenceTypeName());

        if (value.isNull()) {
            resultBuilder.value("null");
        } else if (value instanceof IJavaArray array) {
            int length = array.getLength();
            resultBuilder.value(value.getReferenceTypeName() + "[" + length + "]")
                    .length(length);

            // Show first N elements
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
            resultBuilder.elements(elements);
            if (length > preview) {
                resultBuilder.truncated(true);
            }
        } else if (value instanceof IJavaObject) {
            resultBuilder.value(value.getValueString());
            // List field names for drill-down
            List<String> fieldNames = new ArrayList<>();
            for (IVariable v : value.getVariables()) {
                fieldNames.add(v.getName());
            }
            if (!fieldNames.isEmpty()) {
                resultBuilder.fields(fieldNames);
            }
        } else {
            // Primitive or null
            resultBuilder.value(value.getValueString());
        }

        return resultBuilder.build();
    }
}
