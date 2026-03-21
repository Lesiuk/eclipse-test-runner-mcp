package uk.l3si.eclipse.mcp.debugging.tools;

import com.sun.jdi.InvalidStackFrameException;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.VariableCollector;
import uk.l3si.eclipse.mcp.debugging.model.ExpressionResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.StackTraceFilter;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
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
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InvocationException;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import org.eclipse.jdt.internal.debug.core.model.JDIThread;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class EvaluateExpressionTool implements McpTool {

    private static final long EVAL_TIMEOUT_MS = 60_000;
    private static final int MAX_ARRAY_PREVIEW = 10;
    private static final int MAX_STACK_FRAMES = 10;

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
             + "Can read variables, inspect fields, call methods, or check conditions. "
             + "Collections and maps are shown as JSON arrays/objects. "
             + "Tip: inspect multiple fields at once with 'obj.getId() + \"|\" + obj.getName()' "
             + "or use 'java.util.Arrays.asList(obj.getId(), obj.getName())'. "
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
    public Object execute(Args args, ProgressReporter progress) throws Exception {
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
        progress.report("Waiting for previous evaluation...");
        if (!EVAL_LOCK.tryAcquire(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                    "Timed out waiting for a previous evaluation to complete.");
        }
        try {
            return doEvaluate(expression, frame, javaProject, target);
        } catch (InvalidStackFrameException e) {
            throw new IllegalStateException(
                    "Stack frame is no longer valid — this commonly happens after a previous "
                    + "expression evaluation threw an exception in the target VM. "
                    + "Use 'get_debug_state' to check thread state, then 'step' to get a fresh frame.");
        } finally {
            EVAL_LOCK.release();
        }
    }

    private Object doEvaluate(String expression, IJavaStackFrame frame,
            IJavaProject javaProject, IJavaDebugTarget target) throws Exception {

        // Validate frame early — gives a clear message instead of the cryptic
        // "InvalidStackFrameException occurred retrieving 'this'" from the engine
        try {
            frame.getLineNumber();
        } catch (DebugException e) {
            if (e.getCause() instanceof InvalidStackFrameException
                    || e.getStatus().getException() instanceof InvalidStackFrameException) {
                throw new IllegalStateException(
                        "Stack frame is no longer valid — this commonly happens after a "
                        + "previous expression evaluation threw an exception in the target VM. "
                        + "Use 'get_debug_state' to check thread state, then 'step' to get a fresh frame.");
            }
            throw e;
        }

        IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(
                javaProject, target);

        try {
            // Try safe evaluation first: wrap in try-catch so exceptions in the
            // target VM are caught without invalidating the stack frame.
            IEvaluationResult safeResult = evaluateSnippet(
                    wrapInTryCatch(expression), frame, engine);

            if (!safeResult.hasErrors()) {
                IJavaValue value = safeResult.getValue();
                if (isThrowableInstance(value)) {
                    // Expression threw — extract details (frame is still valid!)
                    throw new RuntimeException(formatCaughtException((IJavaObject) value)
                            + "\n\nThe stack frame is still valid — you can "
                            + "evaluate a different expression.");
                }
                return formatResult(expression, value);
            }

            // Value-returning wrapper failed — try void-compatible wrapper
            // (handles void methods like System.out.println, setters, etc.)
            IEvaluationResult voidResult = evaluateSnippet(
                    wrapVoidInTryCatch(expression), frame, engine);

            if (!voidResult.hasErrors()) {
                IJavaValue voidValue = voidResult.getValue();
                if (isThrowableInstance(voidValue)) {
                    throw new RuntimeException(formatCaughtException((IJavaObject) voidValue)
                            + "\n\nThe stack frame is still valid — you can "
                            + "evaluate a different expression.");
                }
                return formatResult(expression, voidValue);
            }

            // Both wrappers failed — fall back to direct evaluation.
            IEvaluationResult evalResult = evaluateSnippet(
                    expression, frame, engine);

            if (evalResult.hasErrors()) {
                String[] messages = evalResult.getErrorMessages();
                String errorMsg = messages != null && messages.length > 0
                        ? String.join("; ", Arrays.stream(messages).distinct().toArray(String[]::new))
                        : "Expression evaluation failed";
                if (evalResult.getException() != null) {
                    errorMsg += ": " + unwrapEvalException(evalResult.getException(), frame);
                    if (isFrameInvalid(frame)) {
                        errorMsg += "\n\nWARNING: This exception invalidated the "
                                + "current stack frame. Further evaluations will "
                                + "fail until you 'step' to a new location.";
                    }
                } else if (errorMsg.contains("InvalidStackFrameException")) {
                    errorMsg += "\n\nThis commonly happens after a previous "
                            + "expression evaluation threw an exception in the "
                            + "target VM, which invalidated the stack frame. "
                            + "Use 'step' or 'get_debug_state' to re-establish "
                            + "a valid frame before retrying.";
                }
                throw new RuntimeException(errorMsg);
            }

            return formatResult(expression, evalResult.getValue());
        } finally {
            engine.dispose();
        }
    }

    private IEvaluationResult evaluateSnippet(String snippet, IJavaStackFrame frame,
            IAstEvaluationEngine engine) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        IEvaluationResult[] resultHolder = new IEvaluationResult[1];

        IEvaluationListener listener = result -> {
            resultHolder[0] = result;
            latch.countDown();
        };

        engine.evaluate(snippet, frame, listener,
                DebugEvent.EVALUATION, false);

        if (!latch.await(EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                    "Expression evaluation timed out after "
                    + (EVAL_TIMEOUT_MS / 1000) + " seconds.");
        }
        return resultHolder[0];
    }

    static String wrapInTryCatch(String expression) {
        return "try { return (" + expression
                + "); } catch (Throwable __eval_ex) { return __eval_ex; }";
    }

    static String wrapVoidInTryCatch(String expression) {
        return "try { " + expression
                + "; return null; } catch (Throwable __eval_ex) { return __eval_ex; }";
    }

    static boolean isThrowableInstance(IJavaValue value) {
        if (value == null || value.isNull() || !(value instanceof IJavaObject)) {
            return false;
        }
        try {
            IJavaType type = value.getJavaType();
            if (!(type instanceof IJavaClassType classType)) {
                return false;
            }
            while (classType != null) {
                if ("java.lang.Throwable".equals(classType.getName())) {
                    return true;
                }
                classType = classType.getSuperclass();
            }
        } catch (DebugException e) {
            // Can't determine type — assume not a Throwable
        }
        return false;
    }

    /**
     * Format a caught exception from the try-catch wrapper, extracting the
     * type name, message (via getMessage()), and filtered stack trace.
     */
    private String formatCaughtException(IJavaObject throwable) {
        String typeName;
        try {
            typeName = throwable.getReferenceTypeName();
        } catch (DebugException e) {
            typeName = "Unknown";
        }

        String message = null;
        try {
            IJavaThread thread = debugContext.resolveThread(null);
            IJavaValue msgValue = throwable.sendMessage("getMessage",
                    "()Ljava/lang/String;", new IJavaValue[0], thread, false);
            if (msgValue != null && !msgValue.isNull()) {
                message = msgValue.getValueString();
            }
        } catch (Exception ignored) {
        }

        StringBuilder sb = new StringBuilder("Expression threw ");
        sb.append(typeName);
        if (message != null) {
            sb.append(": ").append(message);
        }

        appendCaughtStackTrace(sb, throwable);
        return sb.toString();
    }

    private void appendCaughtStackTrace(StringBuilder sb, IJavaObject throwable) {
        try {
            IJavaThread thread = debugContext.resolveThread(null);
            IJavaValue stValue = throwable.sendMessage("getStackTrace",
                    "()[Ljava/lang/StackTraceElement;", new IJavaValue[0],
                    thread, false);
            if (!(stValue instanceof IJavaArray array) || array.getLength() == 0) {
                return;
            }

            int kept = 0;
            int omitted = 0;
            for (int i = 0; i < array.getLength(); i++) {
                IJavaValue elem = array.getValue(i);
                if (!(elem instanceof IJavaObject steObj)) continue;

                String steString = invokeToString(steObj);
                if (steString == null) continue;

                // Extract class name for framework filtering
                String className = null;
                int parenIdx = steString.indexOf('(');
                if (parenIdx > 0) {
                    int lastDot = steString.lastIndexOf('.', parenIdx);
                    if (lastDot > 0) {
                        className = steString.substring(0, lastDot);
                    }
                }

                if (StackTraceFilter.isFrameworkFrame(className)) {
                    omitted++;
                    continue;
                }
                if (kept >= MAX_STACK_FRAMES) {
                    omitted++;
                    continue;
                }
                if (omitted > 0 && kept > 0) {
                    sb.append("\n\t... ").append(omitted).append(" more");
                    omitted = 0;
                }

                sb.append("\n\tat ").append(steString);
                kept++;
            }
            if (omitted > 0) {
                sb.append("\n\t... ").append(omitted).append(" more");
            }
        } catch (Exception ignored) {
        }
    }

    private ExpressionResult formatResult(String expression, IJavaValue value)
            throws DebugException {
        ExpressionResult.ExpressionResultBuilder resultBuilder = ExpressionResult.builder()
                .type(value.getReferenceTypeName());

        if (value.isNull()) {
            resultBuilder.value("null");
        } else if (value instanceof IJavaArray array) {
            formatArray(resultBuilder, array);
        } else if (value instanceof IJavaObject obj) {
            formatObject(resultBuilder, obj);
        } else {
            resultBuilder.value(value.getValueString());
        }

        return resultBuilder.build();
    }

    private void formatArray(ExpressionResult.ExpressionResultBuilder resultBuilder,
            IJavaArray array) throws DebugException {
        int length = array.getLength();
        resultBuilder.length(length);

        int preview = Math.min(length, MAX_ARRAY_PREVIEW);
        List<String> items = new ArrayList<>();
        for (int i = 0; i < preview; i++) {
            IJavaValue elem = array.getValue(i);
            items.add(elem.isNull() ? "null" : elementToString(elem));
        }
        resultBuilder.value(items);
        if (length > preview) {
            resultBuilder.truncated(true);
        }
    }

    private void formatObject(ExpressionResult.ExpressionResultBuilder resultBuilder,
            IJavaObject obj) throws DebugException {
        String typeName = obj.getReferenceTypeName();

        if ("java.lang.String".equals(typeName)) {
            resultBuilder.value(tryParseJsonValue(obj.getValueString()));
            return;
        }

        if (VariableCollector.isWellKnownType(typeName)) {
            resultBuilder.value(invokeToString(obj));
            return;
        }

        if (VariableCollector.isCollectionType(typeName)) {
            formatCollection(resultBuilder, obj);
            return;
        }

        if (VariableCollector.isMapType(typeName)) {
            formatMap(resultBuilder, obj);
            return;
        }

        // General objects: invoke toString() for a useful value, keep fields for drill-down
        String display = invokeToString(obj);
        resultBuilder.value(display != null ? display : obj.getValueString());

        List<String> fieldNames = new ArrayList<>();
        for (IVariable v : obj.getVariables()) {
            fieldNames.add(v.getName());
        }
        if (!fieldNames.isEmpty()) {
            resultBuilder.fields(fieldNames);
        }
    }

    private void formatCollection(ExpressionResult.ExpressionResultBuilder resultBuilder,
            IJavaObject obj) throws DebugException {
        int size = invokeSize(obj);
        resultBuilder.length(size);

        try {
            IJavaValue arrayValue = obj.sendMessage("toArray",
                    "()[Ljava/lang/Object;", new IJavaValue[0],
                    debugContext.resolveThread(null), false);

            if (arrayValue instanceof IJavaArray array) {
                int preview = Math.min(array.getLength(), MAX_ARRAY_PREVIEW);
                List<String> items = new ArrayList<>();
                for (int i = 0; i < preview; i++) {
                    IJavaValue elem = array.getValue(i);
                    items.add(elem.isNull() ? "null" : elementToString(elem));
                }
                resultBuilder.value(items);
                if (array.getLength() > preview) {
                    resultBuilder.truncated(true);
                }
                return;
            }
        } catch (Exception ignored) {
        }

        String display = invokeToString(obj);
        resultBuilder.value(display != null ? truncate(display, 500) : obj.getValueString());
    }

    private void formatMap(ExpressionResult.ExpressionResultBuilder resultBuilder,
            IJavaObject obj) throws DebugException {
        int size = invokeSize(obj);
        resultBuilder.length(size);

        try {
            // invoke entrySet().toArray() to get entries
            IJavaValue entrySet = obj.sendMessage("entrySet",
                    "()Ljava/util/Set;", new IJavaValue[0],
                    debugContext.resolveThread(null), false);

            if (entrySet instanceof IJavaObject entrySetObj) {
                IJavaValue arrayValue = entrySetObj.sendMessage("toArray",
                        "()[Ljava/lang/Object;", new IJavaValue[0],
                        debugContext.resolveThread(null), false);

                if (arrayValue instanceof IJavaArray array) {
                    int preview = Math.min(array.getLength(), MAX_ARRAY_PREVIEW);
                    Map<String, String> entries = new LinkedHashMap<>();
                    for (int i = 0; i < preview; i++) {
                        IJavaValue entry = array.getValue(i);
                        if (entry instanceof IJavaObject entryObj) {
                            String key = elementToString(
                                    entryObj.sendMessage("getKey",
                                            "()Ljava/lang/Object;", new IJavaValue[0],
                                            debugContext.resolveThread(null), false));
                            String val = elementToString(
                                    entryObj.sendMessage("getValue",
                                            "()Ljava/lang/Object;", new IJavaValue[0],
                                            debugContext.resolveThread(null), false));
                            entries.put(key, val);
                        }
                    }
                    resultBuilder.value(entries);
                    if (array.getLength() > preview) {
                        resultBuilder.truncated(true);
                    }
                    return;
                }
            }
        } catch (Exception ignored) {
        }

        // Fallback: toString()
        String display = invokeToString(obj);
        resultBuilder.value(display != null ? truncate(display, 500) : obj.getValueString());
    }

    private String invokeToString(IJavaObject obj) {
        try {
            IJavaValue result = obj.sendMessage("toString",
                    "()Ljava/lang/String;", new IJavaValue[0],
                    debugContext.resolveThread(null), false);
            return result != null ? result.getValueString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int invokeSize(IJavaObject obj) {
        try {
            IJavaValue result = obj.sendMessage("size",
                    "()I", new IJavaValue[0],
                    debugContext.resolveThread(null), false);
            return result != null ? Integer.parseInt(result.getValueString()) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private String elementToString(IJavaValue elem) throws DebugException {
        if (elem instanceof IJavaObject obj) {
            String ts = invokeToString(obj);
            return ts != null ? ts : elem.getValueString();
        }
        return elem.getValueString();
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

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

    /** Quick check whether a stack frame is still valid. */
    private static boolean isFrameInvalid(IJavaStackFrame frame) {
        try {
            frame.getLineNumber();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Walk the cause chain of the evaluation exception to find the real
     * target-VM exception type (typically hidden inside an InvocationException).
     */
    private static String unwrapEvalException(DebugException ex, IJavaStackFrame frame) {
        Throwable root = ex.getStatus() != null
                ? ex.getStatus().getException() : ex.getCause();
        for (Throwable t = root; t != null; t = t.getCause()) {
            if (t instanceof InvocationException invEx) {
                try {
                    ObjectReference objRef = invEx.exception();
                    String name = objRef.type().name();
                    String message = readDetailMessage(objRef, frame);
                    String result = message != null
                            ? name + ": " + message
                            : name + " thrown in target VM";
                    String stackTrace = readExceptionStackTrace(objRef, frame);
                    if (stackTrace != null) {
                        result += stackTrace;
                    }
                    return result;
                } catch (Exception ignored) {
                    break;
                }
            }
        }
        return ex.getMessage();
    }

    /**
     * Invoke {@code getStackTrace()} on the exception in the target VM and
     * format the resulting {@code StackTraceElement[]} as a standard Java
     * stack trace string. Returns {@code null} if the stack trace cannot be
     * retrieved (best-effort).
     */
    private static String readExceptionStackTrace(ObjectReference objRef, IJavaStackFrame frame) {
        try {
            IJavaThread javaThread = (IJavaThread) frame.getThread();
            if (!(javaThread instanceof JDIThread jdiThread)) return null;
            ThreadReference threadRef = jdiThread.getUnderlyingThread();

            if (!(objRef.referenceType() instanceof ClassType classType)) return null;
            Method getStackTrace = classType.concreteMethodByName(
                    "getStackTrace", "()[Ljava/lang/StackTraceElement;");
            if (getStackTrace == null) return null;

            Value result = objRef.invokeMethod(
                    threadRef, getStackTrace, Collections.emptyList(),
                    ObjectReference.INVOKE_SINGLE_THREADED);

            if (!(result instanceof ArrayReference stackArray) || stackArray.length() == 0) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            int kept = 0;
            int omitted = 0;
            for (int i = 0; i < stackArray.length(); i++) {
                Value elem = stackArray.getValue(i);
                if (!(elem instanceof ObjectReference steRef)) continue;

                String className = readStringField(steRef, "declaringClass");
                if (StackTraceFilter.isFrameworkFrame(className)) {
                    omitted++;
                    continue;
                }
                if (kept >= MAX_STACK_FRAMES) {
                    omitted++;
                    continue;
                }
                if (omitted > 0) {
                    if (kept > 0) {
                        sb.append("\n\t... ").append(omitted).append(" more");
                    }
                    omitted = 0;
                }
                String methodName = readStringField(steRef, "methodName");
                String fileName = readStringField(steRef, "fileName");
                int lineNumber = readIntField(steRef, "lineNumber");
                sb.append("\n\tat ")
                  .append(className != null ? className : "Unknown")
                  .append('.').append(methodName != null ? methodName : "unknown")
                  .append('(');
                if (fileName != null) {
                    sb.append(fileName);
                    if (lineNumber >= 0) sb.append(':').append(lineNumber);
                } else {
                    sb.append("Unknown Source");
                }
                sb.append(')');
                kept++;
            }
            if (omitted > 0) {
                sb.append("\n\t... ").append(omitted).append(" more");
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readStringField(ObjectReference objRef, String fieldName) {
        try {
            Field field = objRef.referenceType().fieldByName(fieldName);
            if (field == null) return null;
            Value value = objRef.getValue(field);
            if (value instanceof StringReference strRef) {
                return strRef.value();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int readIntField(ObjectReference objRef, String fieldName) {
        try {
            Field field = objRef.referenceType().fieldByName(fieldName);
            if (field == null) return -1;
            Value value = objRef.getValue(field);
            if (value instanceof IntegerValue intVal) {
                return intVal.value();
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Read the exception message by invoking {@code getMessage()} on the
     * exception object in the target VM.  This is necessary because JDK 14+
     * helpful NullPointerException messages are computed lazily by
     * {@code getMessage()} and are NOT stored in the {@code detailMessage}
     * field.  Falls back to reading the field directly if invocation fails
     * (e.g. thread not suspended).
     */
    private static String readDetailMessage(ObjectReference objRef,
            IJavaStackFrame frame) {
        // Try invoking getMessage() first — captures lazy messages (helpful NPEs)
        try {
            IJavaThread javaThread = (IJavaThread) frame.getThread();
            if (javaThread instanceof JDIThread jdiThread) {
                ThreadReference threadRef = jdiThread.getUnderlyingThread();
                if (objRef.referenceType() instanceof ClassType classType) {
                    Method getMessage = classType.concreteMethodByName(
                            "getMessage", "()Ljava/lang/String;");
                    if (getMessage != null) {
                        Value result = objRef.invokeMethod(
                                threadRef, getMessage, Collections.emptyList(),
                                ObjectReference.INVOKE_SINGLE_THREADED);
                        if (result instanceof StringReference strRef) {
                            return strRef.value();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // Fallback: read the detailMessage field directly
        try {
            Field field = objRef.referenceType().fieldByName("detailMessage");
            if (field == null) return null;
            Value value = objRef.getValue(field);
            if (value instanceof StringReference strRef) {
                return strRef.value();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
