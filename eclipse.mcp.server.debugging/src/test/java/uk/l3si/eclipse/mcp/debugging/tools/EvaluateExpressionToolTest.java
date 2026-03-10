package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.EvaluationManager;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvaluateExpressionToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private EvaluateExpressionTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new EvaluateExpressionTool(debugContext);
    }

    @Test
    void nameIsEvaluateExpression() {
        assertEquals("evaluate_expression", tool.getName());
    }

    @Test
    void missingExpressionThrows() {
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("expression"));
    }

    @Test
    void blankExpressionThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("expression", "   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    void successfulEvaluation() throws Exception {
        // Set up mocks
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        // Set up the result
        IJavaValue resultValue = mock(IJavaValue.class);
        when(resultValue.getReferenceTypeName()).thenReturn("int");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("42");
        when(resultValue.getVariables()).thenReturn(new IVariable[]{});

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(false);
        when(evalResult.getValue()).thenReturn(resultValue);

        // Mock the engine to invoke the listener immediately
        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(evalResult);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x + 1");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("x + 1", result.get("expression").getAsString());
            assertEquals("int", result.get("type").getAsString());
            assertEquals("42", result.get("value").getAsString());
        }
    }

    @Test
    void evaluationWithErrors() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(true);
        when(evalResult.getErrorMessages()).thenReturn(new String[]{"Syntax error"});
        when(evalResult.getException()).thenReturn(null);

        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(evalResult);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "invalid!!!");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tool.execute(new Args(args)));
            assertTrue(ex.getMessage().contains("Syntax error"));
        }
    }

    @Test
    void noActiveTarget() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(null);

        JsonObject args = new JsonObject();
        args.addProperty("expression", "x");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("No active debug target"));
    }

    @Test
    void noJavaProject() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class)) {
            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(null);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args)));
            assertTrue(ex.getMessage().contains("Java project"));
        }
    }

    @Test
    void evaluationErrorWithException() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(true);
        when(evalResult.getErrorMessages()).thenReturn(new String[]{"NPE"});
        when(evalResult.getException()).thenReturn(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test", "null ref")));

        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(evalResult);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "obj.method()");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tool.execute(new Args(args)));
            assertTrue(ex.getMessage().contains("NPE"));
            assertTrue(ex.getMessage().contains("null ref"));
        }
    }

    @Test
    void engineIsDisposedAfterEvaluation() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        IJavaValue resultValue = mock(IJavaValue.class);
        when(resultValue.getReferenceTypeName()).thenReturn("int");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("1");
        when(resultValue.getVariables()).thenReturn(new IVariable[]{});

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(false);
        when(evalResult.getValue()).thenReturn(resultValue);

        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(evalResult);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "1");

            tool.execute(new Args(args));
            verify(engine).dispose();
        }
    }

    @Test
    void engineDisposedEvenOnError() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(true);
        when(evalResult.getErrorMessages()).thenReturn(new String[]{"error"});
        when(evalResult.getException()).thenReturn(null);

        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(evalResult);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "bad");

            assertThrows(RuntimeException.class, () -> tool.execute(new Args(args)));
            verify(engine).dispose();
        }
    }
}
