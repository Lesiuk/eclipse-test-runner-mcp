package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ArrayElementInfo;
import uk.l3si.eclipse.mcp.debugging.model.ExpressionResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.EvaluationManager;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.launching.JavaRuntime;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class EvaluateExpressionTool implements McpTool {

    private static final long EVAL_TIMEOUT_MS = 60_000;
    private static final int MAX_ARRAY_PREVIEW = 10;

    /** Only one evaluation can run at a time (Eclipse JDT limitation). */
    private static final Semaphore EVAL_LOCK = new Semaphore(1, true);

    private final DebugContext debugContext;

    public EvaluateExpressionTool(DebugContext debugContext) {
        this.debugContext = debugContext;
    }

    @Override
    public String getName() {
        return "evaluate_expression";
    }

    @Override
    public String getDescription() {
        return "Evaluate a Java expression in the context of the current stack frame. "
             + "Can access local variables, inspect fields, call methods, check conditions, or modify state. "
             + "For objects, returns the list of field names. For arrays, returns the size and first elements. "
             + "Examples: 'myList.size()', 'myObj.field', 'x > 0 && y != null', 'obj.toString()'. "
             + "The thread must be suspended at a breakpoint or after a step.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("expression", PropertySchema.string("Java expression to evaluate"))
                .property("thread_id", PropertySchema.builder().type("integer").description("Thread ID. Defaults to current suspended thread.").build())
                .property("frame_index", PropertySchema.builder().type("integer").description("Stack frame index (0 = top). Defaults to 0.").build())
                .required(List.of("expression"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String expression = args.requireString("expression", "Java expression");
        if (expression.isBlank()) {
            throw new IllegalArgumentException("'expression' must not be blank. Provide a Java expression (e.g. 'myList.size()', 'x > 0').");
        }
        Long threadId = args.getLong("thread_id");
        Integer frameIndex = args.getInt("frame_index");

        IJavaThread thread = debugContext.resolveThread(threadId);
        IJavaStackFrame frame = debugContext.resolveFrame(thread, frameIndex);
        IJavaDebugTarget target = debugContext.getCurrentTarget();

        if (target == null) {
            throw new IllegalStateException("No active debug target.");
        }

        // Resolve the IJavaProject from the launch configuration
        IJavaProject javaProject = JavaRuntime.getJavaProject(
                target.getLaunch().getLaunchConfiguration());
        if (javaProject == null) {
            throw new IllegalStateException(
                    "Cannot determine Java project from launch configuration.");
        }

        // Serialize evaluations — Eclipse JDT does not support concurrent evals
        if (!EVAL_LOCK.tryAcquire(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                    "Timed out waiting for a previous evaluation to complete.");
        }
        try {
            return doEvaluate(expression, frame, javaProject, target);
        } finally {
            EVAL_LOCK.release();
        }
    }

    private Object doEvaluate(String expression, IJavaStackFrame frame,
            IJavaProject javaProject, IJavaDebugTarget target) throws Exception {
        IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(
                javaProject, target);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            IEvaluationResult[] resultHolder = new IEvaluationResult[1];

            IEvaluationListener listener = result -> {
                resultHolder[0] = result;
                latch.countDown();
            };

            engine.evaluate(expression, frame, listener,
                    DebugEvent.EVALUATION, false);

            if (!latch.await(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Expression evaluation timed out after "
                        + (EVAL_TIMEOUT_MS / 1000) + " seconds.");
            }

            IEvaluationResult evalResult = resultHolder[0];
            if (evalResult.hasErrors()) {
                String[] messages = evalResult.getErrorMessages();
                String errorMsg = messages != null && messages.length > 0
                        ? String.join("; ", messages)
                        : "Expression evaluation failed";
                if (evalResult.getException() != null) {
                    errorMsg += ": " + unwrapEvalException(evalResult.getException());
                }
                throw new RuntimeException(errorMsg);
            }

            IJavaValue value = evalResult.getValue();
            return formatResult(expression, value);
        } finally {
            engine.dispose();
        }
    }

    private ExpressionResult formatResult(String expression, IJavaValue value)
            throws DebugException {
        ExpressionResult.ExpressionResultBuilder resultBuilder = ExpressionResult.builder()
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
            String rawValue = value.getValueString();
            if ("java.lang.String".equals(value.getReferenceTypeName())) {
                resultBuilder.value(tryParseJsonValue(rawValue));
            } else {
                resultBuilder.value(rawValue);
            }
            List<String> fieldNames = new ArrayList<>();
            for (IVariable v : value.getVariables()) {
                fieldNames.add(v.getName());
            }
            if (!fieldNames.isEmpty()) {
                resultBuilder.fields(fieldNames);
            }
        } else {
            resultBuilder.value(value.getValueString());
        }

        return resultBuilder.build();
    }

    /**
     * Try to parse a string as JSON. If it's a valid JSON object or array,
     * return the parsed {@link JsonElement} so Gson serialises it inline
     * instead of double-encoding it as a string.
     */
    private static Object tryParseJsonValue(String raw) {
        if (raw != null && raw.length() >= 2
                && (raw.charAt(0) == '{' || raw.charAt(0) == '[')) {
            try {
                JsonElement el = JsonParser.parseString(raw);
                if (el.isJsonObject() || el.isJsonArray()) {
                    return el;
                }
            } catch (JsonSyntaxException ignored) {
            }
        }
        return raw;
    }

    /**
     * Walk the cause chain of the evaluation exception to find the real
     * target-VM exception type (typically hidden inside an InvocationException).
     */
    private static String unwrapEvalException(DebugException ex) {
        Throwable root = ex.getStatus() != null
                ? ex.getStatus().getException() : ex.getCause();
        for (Throwable t = root; t != null; t = t.getCause()) {
            if ("com.sun.jdi.InvocationException".equals(t.getClass().getName())) {
                try {
                    Object objRef = t.getClass().getMethod("exception").invoke(t);
                    Object type = objRef.getClass().getMethod("type").invoke(objRef);
                    String name = (String) type.getClass().getMethod("name").invoke(type);
                    return name + " thrown in target VM";
                } catch (ReflectiveOperationException ignored) {
                    break;
                }
            }
        }
        return ex.getMessage();
    }
}
