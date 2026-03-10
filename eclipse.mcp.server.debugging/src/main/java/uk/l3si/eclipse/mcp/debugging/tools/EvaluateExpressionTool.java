package uk.l3si.eclipse.mcp.debugging.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.model.ExpressionResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EvaluateExpressionTool implements IMcpTool {

    private static final long EVAL_TIMEOUT_MS = 30_000;

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
             + "Can access local variables, call methods, check conditions, or modify state. "
             + "Examples: 'myList.size()', 'x > 0 && y != null', 'myField = 42', 'obj.toString()'. "
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

        // Create evaluation engine
        IAstEvaluationEngine engine = EvaluationManager.newAstEvaluationEngine(
                javaProject, target);

        try {
            // Evaluate asynchronously and wait
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
                    errorMsg += ": " + evalResult.getException().getMessage();
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
                .expression(expression)
                .type(value.getReferenceTypeName());

        if (value.isNull()) {
            resultBuilder.value("null");
        } else if (value instanceof IJavaArray array) {
            int length = array.getLength();
            resultBuilder.value(value.getReferenceTypeName() + "[" + length + "]")
                    .length(length);
        } else if (value instanceof IJavaObject) {
            resultBuilder.value(value.getValueString());
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
}
