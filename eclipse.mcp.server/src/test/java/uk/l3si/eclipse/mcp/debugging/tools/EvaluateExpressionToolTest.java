package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jdi.InvalidStackFrameException;
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
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("expression"));
    }

    @Test
    void blankExpressionThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("expression", "   ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
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
                    () -> tool.execute(new Args(args), message -> {}));
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
                    () -> tool.execute(new Args(args), message -> {}));
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
                () -> tool.execute(new Args(args), message -> {}));
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
                    () -> tool.execute(new Args(args), message -> {}));
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
                    () -> tool.execute(new Args(args), message -> {}));
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

            tool.execute(new Args(args), message -> {});
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

            assertThrows(RuntimeException.class, () -> tool.execute(new Args(args), message -> {}));
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
            assertEquals("java.util.HashMap", result.get("type").getAsString());
            assertEquals(2, result.get("length").getAsInt());
            assertTrue(result.get("value").isJsonObject());
            JsonObject valueObj = result.get("value").getAsJsonObject();
            assertEquals("Alice", valueObj.get("name").getAsString());
            assertEquals("30", valueObj.get("age").getAsString());
        }
    }

    // --- InvalidStackFrameException handling tests ---

    @Test
    void frameInvalidBeforeEvaluation_throwsWithHelpfulMessage() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        // Frame is already invalid when we try to evaluate
        when(frame.getLineNumber()).thenThrow(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test",
                        "frame invalid",
                        new InvalidStackFrameException())));

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class)) {
            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("Stack frame is no longer valid"));
            assertTrue(ex.getMessage().contains("commonly happens"));
            assertTrue(ex.getMessage().contains("step"));
        }
    }

    @Test
    void frameInvalidBeforeEvaluation_otherDebugExceptionPropagates() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        // Frame throws DebugException but NOT caused by InvalidStackFrameException
        when(frame.getLineNumber()).thenThrow(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test",
                        "timeout",
                        new java.util.concurrent.TimeoutException("took too long"))));

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class)) {
            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            DebugException ex = assertThrows(DebugException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getCause() instanceof java.util.concurrent.TimeoutException);
        }
    }

    @Test
    void frameInvalidBeforeEvaluation_noEngineCreated() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        when(frame.getLineNumber()).thenThrow(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test",
                        "gone", new InvalidStackFrameException())));

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));

            // Verify engine was never created because we failed before that
            evalManagerMock.verifyNoInteractions();
        }
    }

    @Test
    void evaluationError_reportsErrorDirectly() throws Exception {
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

        when(frame.getLineNumber()).thenReturn(42);

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(true);
        when(evalResult.getErrorMessages()).thenReturn(
                new String[]{"Expression evaluation failed"});
        when(evalResult.getException()).thenReturn(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test",
                        "NullPointerException")));

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
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("Expression evaluation failed"));
            assertTrue(ex.getMessage().contains("NullPointerException"));
            // Only 1 evaluation call (direct, no wrappers)
            verify(engine, times(1)).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());
        }
    }

    @Test
    void errorMessageContainingInvalidStackFrameException_reportsRawError() throws Exception {
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
        when(frame.getLineNumber()).thenReturn(42);

        // Eclipse engine reports InvalidStackFrameException in error message, no exception object
        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(true);
        when(evalResult.getErrorMessages()).thenReturn(new String[]{
                "com.sun.jdi.InvalidStackFrameException occurred retrieving 'this' from stack frame."});
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
            args.addProperty("expression", "driver.getPageSource()");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            // Original engine message is reported directly
            assertTrue(ex.getMessage().contains("InvalidStackFrameException"));
        }
    }

    @Test
    void regularErrorMessage_noExtraInvalidFrameContext() throws Exception {
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
        when(frame.getLineNumber()).thenReturn(42);

        IEvaluationResult evalResult = mock(IEvaluationResult.class);
        when(evalResult.hasErrors()).thenReturn(true);
        when(evalResult.getErrorMessages()).thenReturn(
                new String[]{"Syntax error: unexpected token"});
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
            args.addProperty("expression", "bad syntax!!!");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertEquals("Syntax error: unexpected token", ex.getMessage());
            assertFalse(ex.getMessage().contains("commonly happens"),
                    "Should NOT add invalid-frame context for regular errors");
        }
    }

    @Test
    void invalidStackFrameExceptionDuringFormatting_retriesThenFails() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaStackFrame freshFrame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null))
                .thenReturn(frame)        // first call from execute
                .thenReturn(freshFrame);  // retry after InvalidStackFrameException
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);
        when(frame.getLineNumber()).thenReturn(42);
        when(freshFrame.getLineNumber()).thenReturn(42);

        // Evaluation succeeds but frame becomes invalid during result formatting
        IJavaValue resultValue = mock(IJavaValue.class);
        when(resultValue.getReferenceTypeName()).thenThrow(
                new InvalidStackFrameException());

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
            args.addProperty("expression", "someValue");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("Stack frame is no longer valid"));
            assertTrue(ex.getMessage().contains("commonly happens"));
            assertTrue(ex.getMessage().contains("step"));

            // Engine should still be disposed even on failure (called twice: first attempt + retry)
            verify(engine, times(2)).dispose();
        }
    }

    @Test
    void invalidStackFrameExceptionDuringEvalLatchWait_retriesThenFails() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaStackFrame freshFrame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);
        IAstEvaluationEngine engine = mock(IAstEvaluationEngine.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null))
                .thenReturn(frame)        // first call from execute
                .thenReturn(freshFrame);  // retry after InvalidStackFrameException
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);
        when(frame.getLineNumber()).thenReturn(42);
        when(freshFrame.getLineNumber()).thenReturn(42);

        // Engine.evaluate throws InvalidStackFrameException on both attempts
        doThrow(new InvalidStackFrameException())
                .when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("Stack frame is no longer valid"));
            assertTrue(ex.getMessage().contains("commonly happens"));
        }
    }

    @Test
    void frameInvalidBeforeEvaluation_semaphoreReleased() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        when(frame.getLineNumber()).thenThrow(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test",
                        "gone", new InvalidStackFrameException())));

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class)) {
            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            // First call fails with invalid frame
            assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));

            // Second call should NOT hang on semaphore (proves it was released)
            assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
        }
    }

    @Test
    void frameInvalidViaThis_detectedEarly() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        IJavaStackFrame frame = mock(IJavaStackFrame.class);
        IJavaDebugTarget target = mock(IJavaDebugTarget.class);
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration launchConfig = mock(ILaunchConfiguration.class);
        IJavaProject javaProject = mock(IJavaProject.class);

        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.resolveFrame(thread, null)).thenReturn(frame);
        when(debugContext.getCurrentTarget()).thenReturn(target);
        when(target.getLaunch()).thenReturn(launch);
        when(launch.getLaunchConfiguration()).thenReturn(launchConfig);

        // getLineNumber succeeds but getThis throws — frame is partially invalid
        when(frame.getLineNumber()).thenReturn(42);
        when(frame.isStatic()).thenReturn(false);
        when(frame.getThis()).thenThrow(new DebugException(
                new org.eclipse.core.runtime.Status(
                        org.eclipse.core.runtime.IStatus.ERROR, "test",
                        "retrieving 'this'",
                        new InvalidStackFrameException())));

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class)) {
            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "x");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("Stack frame is no longer valid"));
        }
    }

    @Test
    void staticFrame_skipsThisCheck() throws Exception {
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
        when(frame.getLineNumber()).thenReturn(42);
        when(frame.isStatic()).thenReturn(true);

        // Evaluation succeeds
        IJavaValue resultValue = mock(IJavaValue.class);
        when(resultValue.getReferenceTypeName()).thenReturn("int");
        when(resultValue.isNull()).thenReturn(false);
        when(resultValue.getValueString()).thenReturn("42");
        when(resultValue.getVariables()).thenReturn(new IVariable[]{});

        IEvaluationResult safeResult = mock(IEvaluationResult.class);
        when(safeResult.hasErrors()).thenReturn(false);
        when(safeResult.getValue()).thenReturn(resultValue);

        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(safeResult);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "42");

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {}))
                    .getAsJsonObject();
            assertEquals("42", result.get("value").getAsString());
            // getThis should NOT have been called on a static frame
            verify(frame, never()).getThis();
        }
    }

    @Test
    void evaluationFails_reportsError() throws Exception {
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
        when(frame.getLineNumber()).thenReturn(42);

        IEvaluationResult evalError = mock(IEvaluationResult.class);
        when(evalError.hasErrors()).thenReturn(true);
        when(evalError.getErrorMessages()).thenReturn(new String[]{"Cannot resolve 'xyz'"});
        when(evalError.getException()).thenReturn(null);

        doAnswer(invocation -> {
            IEvaluationListener listener = invocation.getArgument(2);
            listener.evaluationComplete(evalError);
            return null;
        }).when(engine).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());

        try (MockedStatic<JavaRuntime> javaRuntimeMock = mockStatic(JavaRuntime.class);
             MockedStatic<EvaluationManager> evalManagerMock = mockStatic(EvaluationManager.class)) {

            javaRuntimeMock.when(() -> JavaRuntime.getJavaProject(launchConfig))
                    .thenReturn(javaProject);
            evalManagerMock.when(() -> EvaluationManager.newAstEvaluationEngine(javaProject, target))
                    .thenReturn(engine);

            JsonObject args = new JsonObject();
            args.addProperty("expression", "xyz");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("Cannot resolve 'xyz'"));
            // Only 1 evaluation call (direct, no wrappers)
            verify(engine, times(1)).evaluate(anyString(), any(), any(), anyInt(), anyBoolean());
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

            JsonObject result = GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
            assertEquals("com.example.Order", result.get("type").getAsString());
            assertEquals("Order{id=42, total=99.99}", result.get("value").getAsString());
            assertTrue(result.has("fields"));
            JsonArray fields = result.get("fields").getAsJsonArray();
            assertEquals(2, fields.size());
            assertEquals("id", fields.get(0).getAsString());
            assertEquals("total", fields.get(1).getAsString());
        }
    }

}
