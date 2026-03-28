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
        } catch (InvalidStackFrameException | IllegalStateException e) {
            // Frame was invalidated — try to recover with a fresh frame.
            // The thread is typically still suspended at the same location;
            // Eclipse's frame objects are just stale after a previous evaluation
            // that invoked methods on the target VM (e.g. getMessage() for NPEs).
            // validateFrame throws IllegalStateException wrapping InvalidStackFrameException,
            // so we must catch both.
            if (e instanceof IllegalStateException
                    && (e.getMessage() == null || !e.getMessage().contains("no longer valid"))) {
                throw e; // unrelated IllegalStateException — propagate
            }
            try {
                // JDI method invocations (e.g. getMessage() for NPEs) resume the
                // thread with INVOKE_SINGLE_THREADED, which doesn't fire normal
                // Eclipse debug events.  The thread's frame cache stays stale.
                // Force a refresh so getStackFrames() returns valid frames.
                invalidateFrameCache(thread);
                IJavaStackFrame freshFrame = debugContext.resolveFrame(thread, frameIndex);
                return doEvaluate(expression, freshFrame, javaProject, target);
            } catch (InvalidStackFrameException | IllegalStateException e2) {
                // Recovery failed
            }
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

        // Validate frame early — checks both line-number access AND 'this'
        // access (for non-static frames).  getLineNumber() alone can succeed
        // even when the frame is otherwise invalid; the engine will then fail
        // with "InvalidStackFrameException occurred retrieving 'this'".
        validateFrame(frame);

        // Capture thread reference before evaluation — the frame may become
        // invalid after an evaluation throws, but the thread stays usable
        // for JDI method invocations (e.g. getMessage() for helpful NPEs).
        IJavaThread thread = (IJavaThread) frame.getThread();

        IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(
                javaProject, target);

        try {
            IEvaluationResult evalResult = evaluateSnippet(expression, frame, engine);

            if (evalResult.hasErrors()) {
                String[] messages = evalResult.getErrorMessages();
                String errorMsg = messages != null && messages.length > 0
                        ? String.join("; ", Arrays.stream(messages).distinct().toArray(String[]::new))
                        : "Expression evaluation failed";
                if (evalResult.getException() != null) {
                    errorMsg += ": " + unwrapEvalException(evalResult.getException(), thread);
                }
                throw new RuntimeException(errorMsg);
            }

            return formatResult(expression, evalResult.getValue());
        } finally {
            engine.dispose();
        }
    }

    /**
     * Validate that the stack frame is usable for evaluation.  Checks both
     * line-number access and 'this' access (for non-static frames).
     * {@code getLineNumber()} alone can succeed on an invalidated frame;
     * the engine then fails with a cryptic "retrieving 'this'" message.
     */
    private static void validateFrame(IJavaStackFrame frame) throws Exception {
        try {
            frame.getLineNumber();
            if (!frame.isStatic()) {
                frame.getThis();
            }
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

    /**
     * Force JDIThread to recompute its cached stack frames on the next
     * {@code getStackFrames()} call.  JDI's {@code INVOKE_SINGLE_THREADED}
     * method invocations don't fire normal Eclipse debug events, so the
     * frame cache stays stale after such calls.
     */
    private static void invalidateFrameCache(IJavaThread thread) {
        try {
            // JDIThread.fRefreshChildren controls whether getStackFrames()
            // recomputes or returns cached frames.
            java.lang.reflect.Field refreshField =
                    thread.getClass().getDeclaredField("fRefreshChildren");
            refreshField.setAccessible(true);
            refreshField.set(thread, true);
        } catch (Exception ignored) {
            // Not a JDIThread or field layout changed — best effort
        }
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


    /**
     * Walk the cause chain of the evaluation exception to find the real
     * target-VM exception type (typically hidden inside an InvocationException).
     * Uses JDI field reads where possible to preserve the current stack frame.
     * For NullPointerException, may invoke getMessage() to get JDK 14+ helpful
     * messages (which can invalidate the frame — handled by recovery in execute).
     */
    private static String unwrapEvalException(DebugException ex,
            IJavaThread thread) {
        // Walk the exception chain looking for InvocationException.
        // DebugException nests causes via getStatus().getException(),
        // not getCause(), so we must check both paths.
        InvocationException invEx = findInvocationException(ex);
        if (invEx != null) {
            try {
                ObjectReference objRef = invEx.exception();
                String name = objRef.type().name();
                String message = readDetailMessage(objRef, thread);
                String result = message != null
                        ? name + ": " + message
                        : name + " thrown in target VM";
                String stackTrace = readExceptionStackTrace(objRef);
                if (stackTrace != null) {
                    result += stackTrace;
                }
                return result;
            } catch (Exception ignored) {
            }
        }
        return ex.getMessage();
    }

    /**
     * Search the exception chain for an {@link InvocationException}.
     * Walks both {@code getCause()} and {@code DebugException.getStatus().getException()}
     * chains, since Eclipse nests causes via status rather than standard cause.
     */
    private static InvocationException findInvocationException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof InvocationException inv) return inv;
            if (t instanceof DebugException de && de.getStatus() != null) {
                Throwable statusEx = de.getStatus().getException();
                if (statusEx != null && statusEx != t && statusEx != ex) {
                    InvocationException found = findInvocationException(statusEx);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * Read the stack trace from an exception object using only JDI field reads
     * (no method invocations on the target VM) so the current stack frame is
     * preserved.  Reads the {@code stackTrace} field directly — may return
     * {@code null} if the JVM lazily initializes the field.
     */
    private static String readExceptionStackTrace(ObjectReference objRef) {
        try {
            Field stackTraceField = objRef.referenceType().fieldByName("stackTrace");
            if (stackTraceField == null) return null;
            Value stValue = objRef.getValue(stackTraceField);

            if (!(stValue instanceof ArrayReference stackArray) || stackArray.length() == 0) {
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
     * Read the exception message.  First tries reading the {@code detailMessage}
     * field directly (no method invocation, preserves stack frame).  If the
     * field is null and the exception is a NullPointerException, falls back to
     * invoking {@code getMessage()} via JDI to capture JDK 14+ helpful NPE
     * messages that are computed lazily.  The method invocation may invalidate
     * Eclipse's stack frame objects, but the frame recovery in
     * {@link #execute} handles that for subsequent evaluations.
     */
    private static String readDetailMessage(ObjectReference objRef,
            IJavaThread thread) {
        // Try field read first — does not resume the thread
        try {
            Field field = objRef.referenceType().fieldByName("detailMessage");
            if (field != null) {
                Value value = objRef.getValue(field);
                if (value instanceof StringReference strRef) {
                    return strRef.value();
                }
            }
        } catch (Exception ignored) {
        }

        // Field is null — for NullPointerException, invoke getMessage() to
        // get the JDK 14+ helpful message (e.g. "Cannot invoke method X on null").
        // This resumes the thread briefly, which may invalidate stack frame objects,
        // but the frame recovery in execute() handles that.
        try {
            String typeName = objRef.type().name();
            if (typeName != null && typeName.contains("NullPointerException")) {
                return invokeGetMessage(objRef, thread);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Invoke {@code getMessage()} on an exception object via JDI.
     * Uses the thread reference directly (not the frame, which may be invalid
     * after the evaluation threw).
     */
    private static String invokeGetMessage(ObjectReference objRef,
            IJavaThread thread) {
        try {
            if (!(thread instanceof JDIThread jdiThread)) return null;
            ThreadReference threadRef = jdiThread.getUnderlyingThread();
            if (!(objRef.referenceType() instanceof ClassType classType)) return null;
            Method getMessage = classType.concreteMethodByName(
                    "getMessage", "()Ljava/lang/String;");
            if (getMessage == null) return null;
            Value result = objRef.invokeMethod(
                    threadRef, getMessage, Collections.emptyList(),
                    ObjectReference.INVOKE_SINGLE_THREADED);
            if (result instanceof StringReference strRef) {
                return strRef.value();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
