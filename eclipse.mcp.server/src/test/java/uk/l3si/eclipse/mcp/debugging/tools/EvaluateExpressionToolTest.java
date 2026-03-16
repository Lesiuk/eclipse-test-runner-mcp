package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.core.IJavaProject;
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
import org.eclipse.jdt.launching.JavaRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    void duplicateErrorMessagesAreDeduped() throws Exception {
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
        when(evalResult.getErrorMessages()).thenReturn(new String[]{
                "x cannot be resolved", "x cannot be resolved"});
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
            args.addProperty("expression", "x + x");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tool.execute(new Args(args)));
            assertEquals("x cannot be resolved", ex.getMessage());
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

    @Test
    void stringValueContainingJsonReturnsParsedObject() throws Exception {
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

        IJavaObject resultValue = mock(IJavaObject.class);
        when(resultValue.getReferenceTypeName()).thenReturn("java.lang.String");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("{\"key\":\"value\",\"count\":42}");
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
            args.addProperty("expression", "obj.toJson()");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("java.lang.String", result.get("type").getAsString());
            // value should be a parsed JSON object, not a double-encoded string
            assertTrue(result.get("value").isJsonObject());
            JsonObject valueObj = result.get("value").getAsJsonObject();
            assertEquals("value", valueObj.get("key").getAsString());
            assertEquals(42, valueObj.get("count").getAsInt());
        }
    }

    @Test
    void stringValueWithPlainTextRemainsString() throws Exception {
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

        IJavaObject resultValue = mock(IJavaObject.class);
        when(resultValue.getReferenceTypeName()).thenReturn("java.lang.String");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("hello world");
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
            args.addProperty("expression", "name");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("java.lang.String", result.get("type").getAsString());
            // plain text should remain a string
            assertTrue(result.get("value").isJsonPrimitive());
            assertEquals("hello world", result.get("value").getAsString());
        }
    }

    @Test
    void nullValueReturnsNull() throws Exception {
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
        when(resultValue.getReferenceTypeName()).thenReturn("java.lang.Object");
        when(resultValue.isNull()).thenReturn(true);
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
            args.addProperty("expression", "nullRef");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("null", result.get("value").getAsString());
        }
    }

    @Test
    void wellKnownTypeSkipsFields() throws Exception {
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

        IJavaObject resultValue = mock(IJavaObject.class);
        when(resultValue.getReferenceTypeName()).thenReturn("java.lang.Integer");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("42");

        IVariable valueVar = mock(IVariable.class);
        when(valueVar.getName()).thenReturn("value");
        when(resultValue.getVariables()).thenReturn(new IVariable[]{valueVar});

        IJavaValue toStringResult = mock(IJavaValue.class);
        when(toStringResult.getValueString()).thenReturn("42");
        when(resultValue.sendMessage(eq("toString"), eq("()Ljava/lang/String;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(toStringResult);

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
            args.addProperty("expression", "myInteger");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("java.lang.Integer", result.get("type").getAsString());
            assertEquals("42", result.get("value").getAsString());
            assertFalse(result.has("fields"));
        }
    }

    @Test
    void collectionFormattedAsFlatList() throws Exception {
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

        IJavaObject resultValue = mock(IJavaObject.class);
        when(resultValue.getReferenceTypeName()).thenReturn("java.util.ArrayList");
        when(resultValue.isNull()).thenReturn(false);

        // Mock size()
        IJavaValue sizeResult = mock(IJavaValue.class);
        when(sizeResult.getValueString()).thenReturn("3");
        when(resultValue.sendMessage(eq("size"), eq("()I"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(sizeResult);

        // Mock toArray() -> IJavaArray
        IJavaArray toArrayResult = mock(IJavaArray.class);
        when(toArrayResult.getLength()).thenReturn(3);

        IJavaValue elem0 = mock(IJavaValue.class);
        when(elem0.getValueString()).thenReturn("alpha");
        when(elem0.getReferenceTypeName()).thenReturn("java.lang.String");
        when(elem0.isNull()).thenReturn(false);

        IJavaValue elem1 = mock(IJavaValue.class);
        when(elem1.getValueString()).thenReturn("beta");
        when(elem1.isNull()).thenReturn(false);

        IJavaValue elem2 = mock(IJavaValue.class);
        when(elem2.getValueString()).thenReturn("gamma");
        when(elem2.isNull()).thenReturn(false);

        when(toArrayResult.getValue(0)).thenReturn(elem0);
        when(toArrayResult.getValue(1)).thenReturn(elem1);
        when(toArrayResult.getValue(2)).thenReturn(elem2);

        when(resultValue.sendMessage(eq("toArray"), eq("()[Ljava/lang/Object;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(toArrayResult);

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
            args.addProperty("expression", "myList");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("java.util.ArrayList", result.get("type").getAsString());
            assertEquals(3, result.get("length").getAsInt());
            assertTrue(result.get("value").isJsonArray());
            JsonArray valueArray = result.get("value").getAsJsonArray();
            assertEquals("alpha", valueArray.get(0).getAsString());
            assertEquals("beta", valueArray.get(1).getAsString());
            assertEquals("gamma", valueArray.get(2).getAsString());
            assertFalse(result.has("fields"));
        }
    }

    @Test
    void mapFormattedAsJsonObject() throws Exception {
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

        IJavaObject resultValue = mock(IJavaObject.class);
        when(resultValue.getReferenceTypeName()).thenReturn("java.util.HashMap");
        when(resultValue.isNull()).thenReturn(false);

        // Mock size()
        IJavaValue sizeResult = mock(IJavaValue.class);
        when(sizeResult.getValueString()).thenReturn("2");
        when(resultValue.sendMessage(eq("size"), eq("()I"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(sizeResult);

        // Mock entrySet()
        IJavaObject entrySet = mock(IJavaObject.class);
        when(resultValue.sendMessage(eq("entrySet"), eq("()Ljava/util/Set;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(entrySet);

        // Mock entrySet.toArray()
        IJavaArray entryArray = mock(IJavaArray.class);
        when(entryArray.getLength()).thenReturn(2);
        when(entrySet.sendMessage(eq("toArray"), eq("()[Ljava/lang/Object;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(entryArray);

        // Entry 0: name -> Alice
        IJavaObject entry0 = mock(IJavaObject.class);
        IJavaObject key0 = mock(IJavaObject.class);
        IJavaValue key0ToString = mock(IJavaValue.class);
        when(key0ToString.getValueString()).thenReturn("name");
        when(key0.sendMessage(eq("toString"), eq("()Ljava/lang/String;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(key0ToString);
        when(entry0.sendMessage(eq("getKey"), eq("()Ljava/lang/Object;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(key0);

        IJavaObject val0 = mock(IJavaObject.class);
        IJavaValue val0ToString = mock(IJavaValue.class);
        when(val0ToString.getValueString()).thenReturn("Alice");
        when(val0.sendMessage(eq("toString"), eq("()Ljava/lang/String;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(val0ToString);
        when(entry0.sendMessage(eq("getValue"), eq("()Ljava/lang/Object;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(val0);

        // Entry 1: age -> 30
        IJavaObject entry1 = mock(IJavaObject.class);
        IJavaObject key1 = mock(IJavaObject.class);
        IJavaValue key1ToString = mock(IJavaValue.class);
        when(key1ToString.getValueString()).thenReturn("age");
        when(key1.sendMessage(eq("toString"), eq("()Ljava/lang/String;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(key1ToString);
        when(entry1.sendMessage(eq("getKey"), eq("()Ljava/lang/Object;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(key1);

        IJavaObject val1 = mock(IJavaObject.class);
        IJavaValue val1ToString = mock(IJavaValue.class);
        when(val1ToString.getValueString()).thenReturn("30");
        when(val1.sendMessage(eq("toString"), eq("()Ljava/lang/String;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(val1ToString);
        when(entry1.sendMessage(eq("getValue"), eq("()Ljava/lang/Object;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(val1);

        when(entryArray.getValue(0)).thenReturn(entry0);
        when(entryArray.getValue(1)).thenReturn(entry1);

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
            args.addProperty("expression", "myMap");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("java.util.HashMap", result.get("type").getAsString());
            assertEquals(2, result.get("length").getAsInt());
            assertTrue(result.get("value").isJsonObject());
            JsonObject valueObj = result.get("value").getAsJsonObject();
            assertEquals("Alice", valueObj.get("name").getAsString());
            assertEquals("30", valueObj.get("age").getAsString());
        }
    }

    @Test
    void customObjectUsesToStringAndKeepsFields() throws Exception {
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

        IJavaObject resultValue = mock(IJavaObject.class);
        when(resultValue.getReferenceTypeName()).thenReturn("com.example.Order");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("Order (id=42)");

        IJavaValue toStringResult = mock(IJavaValue.class);
        when(toStringResult.getValueString()).thenReturn("Order{id=42, total=99.99}");
        when(resultValue.sendMessage(eq("toString"), eq("()Ljava/lang/String;"),
                any(IJavaValue[].class), any(IJavaThread.class), eq(false)))
                .thenReturn(toStringResult);

        IVariable idVar = mock(IVariable.class);
        when(idVar.getName()).thenReturn("id");
        IVariable totalVar = mock(IVariable.class);
        when(totalVar.getName()).thenReturn("total");
        when(resultValue.getVariables()).thenReturn(new IVariable[]{idVar, totalVar});

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
            args.addProperty("expression", "myOrder");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
            assertEquals("com.example.Order", result.get("type").getAsString());
            assertEquals("Order{id=42, total=99.99}", result.get("value").getAsString());
            assertTrue(result.has("fields"));
            JsonArray fields = result.get("fields").getAsJsonArray();
            assertEquals(2, fields.size());
            assertEquals("id", fields.get(0).getAsString());
            assertEquals("total", fields.get(1).getAsString());
        }
    }

    @Test
    void isWellKnownTypeTests() {
        assertTrue(EvaluateExpressionTool.isWellKnownType("java.lang.String"));
        assertTrue(EvaluateExpressionTool.isWellKnownType("java.lang.Integer"));
        assertTrue(EvaluateExpressionTool.isWellKnownType("java.lang.Boolean"));
        assertTrue(EvaluateExpressionTool.isWellKnownType("java.math.BigDecimal"));
        assertTrue(EvaluateExpressionTool.isWellKnownType("java.time.LocalDate"));
        assertTrue(EvaluateExpressionTool.isWellKnownType("java.util.UUID"));

        assertFalse(EvaluateExpressionTool.isWellKnownType("com.example.MyClass"));
        assertFalse(EvaluateExpressionTool.isWellKnownType("java.util.ArrayList"));
        assertFalse(EvaluateExpressionTool.isWellKnownType("java.util.HashMap"));
    }

    @Test
    void isCollectionTypeTests() {
        assertTrue(EvaluateExpressionTool.isCollectionType("java.util.ArrayList"));
        assertTrue(EvaluateExpressionTool.isCollectionType("java.util.LinkedList"));
        assertTrue(EvaluateExpressionTool.isCollectionType("java.util.HashSet"));
        assertTrue(EvaluateExpressionTool.isCollectionType("java.util.TreeSet"));
        assertTrue(EvaluateExpressionTool.isCollectionType("java.util.ArrayDeque"));

        assertFalse(EvaluateExpressionTool.isCollectionType("java.util.HashMap"));
        assertFalse(EvaluateExpressionTool.isCollectionType("java.lang.String"));
        assertFalse(EvaluateExpressionTool.isCollectionType("com.example.MyList"));
    }

    @Test
    void isMapTypeTests() {
        assertTrue(EvaluateExpressionTool.isMapType("java.util.HashMap"));
        assertTrue(EvaluateExpressionTool.isMapType("java.util.TreeMap"));
        assertTrue(EvaluateExpressionTool.isMapType("java.util.LinkedHashMap"));

        assertFalse(EvaluateExpressionTool.isMapType("java.util.ArrayList"));
        assertFalse(EvaluateExpressionTool.isMapType("java.lang.String"));
    }
}
