package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ListVariablesResult;
import uk.l3si.eclipse.mcp.debugging.model.VariableResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import com.sun.jdi.InvalidStackFrameException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;

import java.util.ArrayList;
import java.util.List;

public class ListVariablesTool implements McpTool {

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
        return "List all visible variables in the current stack frame with types and values. "
             + "Collections and maps show their contents. Arrays show first elements as a flat list. "
             + "For custom objects, shows field names — use 'evaluate_expression' to inspect them.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("thread_id", PropertySchema.builder().type("integer").description("Thread ID. Defaults to the current suspended thread.").build())
                .property("frame_index", PropertySchema.builder().type("integer").description("Stack frame index (0 = top, from get_stack_trace). Defaults to 0.").build())
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        Long threadId = args.getLong("thread_id");
        Integer frameIndex = args.getInt("frame_index");

        IJavaThread thread = debugContext.resolveThread(threadId);
        if (!thread.isSuspended()) {
            throw new IllegalStateException(
                    "Thread '" + thread.getName() + "' is not suspended. "
                    + "Variables can only be listed when execution is paused at a breakpoint or after a step.");
        }
        IJavaStackFrame frame = debugContext.resolveFrame(thread, frameIndex);

        try {
            List<VariableResult> variables = new ArrayList<>();
            for (IVariable v : frame.getVariables()) {
                try {
                    if (v.getValue() instanceof IJavaValue javaValue) {
                        variables.add(formatValue(v.getName(), javaValue));
                    }
                } catch (DebugException e) {
                    variables.add(VariableResult.builder()
                            .name(v.getName())
                            .type("unknown")
                            .value("<error: " + e.getMessage() + ">")
                            .build());
                }
            }

            return ListVariablesResult.builder()
                    .frame(frame.getDeclaringTypeName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                    .variables(variables)
                    .build();
        } catch (InvalidStackFrameException e) {
            throw new IllegalStateException(
                    "Stack frame is no longer valid — the thread may have resumed or the program terminated. "
                    + "Use 'get_debug_state' to check the current state before retrying.");
        }
    }

    private VariableResult formatValue(String name, IJavaValue value) throws DebugException {
        VariableResult.VariableResultBuilder builder = VariableResult.builder()
                .name(name)
                .type(value.getReferenceTypeName());

        if (value.isNull()) {
            builder.value("null");
        } else if (value instanceof IJavaArray array) {
            int length = array.getLength();
            builder.length(length);

            int preview = Math.min(length, MAX_ARRAY_PREVIEW);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < preview; i++) {
                IJavaValue elem = array.getValue(i);
                items.add(elem.isNull() ? "null" : elem.getValueString());
            }
            builder.value(items);
            if (length > preview) {
                builder.truncated(true);
            }
        } else if (value instanceof IJavaObject obj) {
            String typeName = obj.getReferenceTypeName();
            if (EvaluateExpressionTool.isWellKnownType(typeName)
                    || EvaluateExpressionTool.isCollectionType(typeName)
                    || EvaluateExpressionTool.isMapType(typeName)) {
                // Show toString() for well-known, collection, and map types
                String display = safeToString(obj);
                builder.value(display != null ? display : obj.getValueString());
            } else {
                builder.value(obj.getValueString());
                List<String> fieldNames = new ArrayList<>();
                for (IVariable v : obj.getVariables()) {
                    fieldNames.add(v.getName());
                }
                if (!fieldNames.isEmpty()) {
                    builder.fields(fieldNames);
                }
            }
        } else {
            builder.value(value.getValueString());
        }

        return builder.build();
    }

    /**
     * Collect variables from the given stack frame.
     * Used by StepTool, GetDebugStateTool, and TestLaunchHelper to auto-include
     * variables in stop locations.
     */
    public static List<VariableResult> collectVariables(IJavaStackFrame frame, DebugContext debugContext) {
        try {
            List<VariableResult> variables = new ArrayList<>();
            ListVariablesTool tool = new ListVariablesTool(debugContext);
            for (IVariable v : frame.getVariables()) {
                try {
                    if (v.getValue() instanceof IJavaValue javaValue) {
                        variables.add(tool.formatValue(v.getName(), javaValue));
                    }
                } catch (DebugException e) {
                    variables.add(VariableResult.builder()
                            .name(v.getName())
                            .type("unknown")
                            .value("<error: " + e.getMessage() + ">")
                            .build());
                }
            }
            return variables;
        } catch (Exception e) {
            return null;
        }
    }

    private String safeToString(IJavaObject obj) {
        try {
            IJavaThread thread = debugContext.resolveThread(null);
            IJavaValue result = obj.sendMessage("toString",
                    "()Ljava/lang/String;", new IJavaValue[0], thread, false);
            if (result != null) {
                String s = result.getValueString();
                return s.length() > 200 ? s.substring(0, 197) + "..." : s;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
