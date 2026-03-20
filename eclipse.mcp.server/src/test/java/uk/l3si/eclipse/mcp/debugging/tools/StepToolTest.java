package uk.l3si.eclipse.mcp.debugging.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.l3si.eclipse.mcp.core.tools.TestResultsHelper;
import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.model.LocationInfo;
import uk.l3si.eclipse.mcp.model.TestFailureInfo;
import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StepToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private StepTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new StepTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    @Test
    void nameIsStep() {
        assertEquals("step", tool.getName());
    }

    @Test
    void missingActionThrows() {
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("action"));
    }

    @Test
    void invalidActionThrows() {
        JsonObject args = new JsonObject();
        args.addProperty("action", "jump");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("jump"));
        assertTrue(ex.getMessage().contains("over"));
    }

    @Test
    void notSuspendedThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("not suspended"));
    }

    @Test
    void stepOverSuccess() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");
        when(debugContext.getCurrentLocation()).thenReturn(
                LocationInfo.builder()
                        .className("com.example.App")
                        .method("run")
                        .line(15)
                        .sourceName("App.java")
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertEquals("over", result.get("action").getAsString());
        assertEquals("main", result.get("thread").getAsString());
        assertEquals("breakpoint", result.get("reason").getAsString());

        JsonObject location = result.getAsJsonObject("location");
        assertEquals("com.example.App", location.get("class").getAsString());
        assertEquals("run", location.get("method").getAsString());
        assertEquals(15, location.get("line").getAsInt());

        verify(thread).stepOver();
    }

    @Test
    void stepIntoCallsStepInto() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("suspended");

        JsonObject args = new JsonObject();
        args.addProperty("action", "into");

        tool.execute(new Args(args), message -> {});
        verify(thread).stepInto();
    }

    @Test
    void stepReturnCallsStepReturn() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("suspended");

        JsonObject args = new JsonObject();
        args.addProperty("action", "return");

        tool.execute(new Args(args), message -> {});
        verify(thread).stepReturn();
    }

    @Test
    void terminationDuringStep() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TERMINATED);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertTrue(result.get("terminated").getAsBoolean());
        assertEquals("terminated", result.get("reason").getAsString());
    }

    @Test
    void terminationDuringStepWithTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TERMINATED);

        TestRunResult testRunResult = TestRunResult.builder()
                .status("COMPLETED")
                .totalTests(5)
                .passed(5)
                .failed(0)
                .errors(0)
                .ignored(0)
                .elapsedSeconds(0.45)
                .failures(List.of())
                .build();

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(testRunResult);

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("terminated").getAsBoolean());

            JsonObject testResults = result.getAsJsonObject("testResults");
            assertNotNull(testResults, "testResults should be present when test finishes");
            assertEquals("COMPLETED", testResults.get("status").getAsString());
            assertEquals(5, testResults.get("totalTests").getAsInt());
            assertEquals(5, testResults.get("passed").getAsInt());
            assertEquals(0, testResults.get("failed").getAsInt());
        }
    }

    @Test
    void terminationDuringStepWithFailedTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TERMINATED);

        TestRunResult testRunResult = TestRunResult.builder()
                .status("COMPLETED")
                .totalTests(2)
                .passed(1)
                .failed(1)
                .errors(0)
                .ignored(0)
                .elapsedSeconds(2.1)
                .failures(List.of(
                        TestFailureInfo.builder()
                                .className("com.example.CalcTest")
                                .method("testAdd")
                                .kind("FAILURE")
                                .message("expected: <4> but was: <5>")
                                .expected("4")
                                .actual("5")
                                .build()))
                .build();

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(testRunResult);

            JsonObject args = new JsonObject();
            args.addProperty("action", "return");
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("terminated").getAsBoolean());

            JsonObject testResults = result.getAsJsonObject("testResults");
            assertNotNull(testResults);
            assertEquals(1, testResults.get("failed").getAsInt());

            JsonArray failures = testResults.getAsJsonArray("failures");
            assertEquals(1, failures.size());
            JsonObject failure = failures.get(0).getAsJsonObject();
            assertEquals("com.example.CalcTest", failure.get("class").getAsString());
            assertEquals("testAdd", failure.get("method").getAsString());
            assertEquals("4", failure.get("expected").getAsString());
            assertEquals("5", failure.get("actual").getAsString());
        }
    }

    @Test
    void terminationDuringStepWithNoTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TERMINATED);

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(null);

            JsonObject args = new JsonObject();
            args.addProperty("action", "over");
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("terminated").getAsBoolean());
            assertFalse(result.has("testResults"), "testResults should be absent when no results available");
        }
    }

    @Test
    void terminationDuringStepCollectionExceptionReturnsNullTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TERMINATED);

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class)))
                    .thenThrow(new RuntimeException("JUnit model unavailable"));

            JsonObject args = new JsonObject();
            args.addProperty("action", "into");
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("terminated").getAsBoolean());
            assertFalse(result.has("testResults"), "testResults should be absent on collection error");
        }
    }

    @Test
    void suspendedDoesNotCollectTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            JsonObject args = new JsonObject();
            args.addProperty("action", "over");
            executeAndSerialize(args);

            mocked.verifyNoInteractions();
        }
    }

    @Test
    void stepTimeout() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TIMEOUT);

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertEquals("timeout", result.get("reason").getAsString());
    }

    @Test
    void timeoutDoesNotCollectTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TIMEOUT);

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            JsonObject args = new JsonObject();
            args.addProperty("action", "over");
            executeAndSerialize(args);

            mocked.verifyNoInteractions();
        }
    }

    @Test
    void stepWithBreakpointHit() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");
        when(debugContext.getCurrentLocation()).thenReturn(
                LocationInfo.builder()
                        .className("com.example.Other")
                        .method("handle")
                        .line(99)
                        .sourceName("Other.java")
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");

        JsonObject result = executeAndSerialize(args);
        assertEquals("over", result.get("action").getAsString());
        assertNotNull(result.getAsJsonObject("location"));
    }

    @Test
    void withThreadId() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("worker");
        when(debugContext.resolveThread(55L)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("suspended");

        JsonObject args = new JsonObject();
        args.addProperty("action", "over");
        args.addProperty("thread_id", "55");

        JsonObject result = executeAndSerialize(args);
        assertEquals("worker", result.get("thread").getAsString());
        verify(debugContext).resolveThread(55L);
    }

    // --- Resume action tests ---

    @Test
    void resumeCallsThreadResume() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");
        when(debugContext.getCurrentLocation()).thenReturn(
                LocationInfo.builder()
                        .className("com.example.App")
                        .method("doStuff")
                        .line(42)
                        .sourceName("App.java")
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("action", "resume");

        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertEquals("resume", result.get("action").getAsString());
        assertEquals("breakpoint", result.get("reason").getAsString());
    }

    @Test
    void resumeWithTimeout() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(eq(1), any())).thenReturn(WaitResult.TIMEOUT);

        JsonObject args = new JsonObject();
        args.addProperty("action", "resume");
        args.addProperty("timeout", 1);

        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertEquals("timeout", result.get("reason").getAsString());
    }

    @Test
    void resumeTerminatesWithTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt(), any())).thenReturn(WaitResult.TERMINATED);

        TestRunResult testRunResult = TestRunResult.builder()
                .status("COMPLETED")
                .totalTests(3)
                .passed(2)
                .failed(1)
                .errors(0)
                .ignored(0)
                .elapsedSeconds(1.23)
                .failures(List.of(
                        TestFailureInfo.builder()
                                .className("com.example.FooTest")
                                .method("testBar")
                                .kind("FAILURE")
                                .message("expected true but was false")
                                .build()))
                .build();

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(testRunResult);

            JsonObject args = new JsonObject();
            args.addProperty("action", "resume");
            JsonObject result = executeAndSerialize(args);

            verify(thread).resume();
            assertTrue(result.get("terminated").getAsBoolean());
            assertEquals("terminated", result.get("reason").getAsString());

            JsonObject testResults = result.getAsJsonObject("testResults");
            assertNotNull(testResults, "testResults should be present when test finishes");
            assertEquals("COMPLETED", testResults.get("status").getAsString());
            assertEquals(3, testResults.get("totalTests").getAsInt());
            assertEquals(1, testResults.get("failed").getAsInt());
        }
    }

    @Test
    void customTimeoutIsUsed() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(eq(10), any())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");

        JsonObject args = new JsonObject();
        args.addProperty("action", "resume");
        args.addProperty("timeout", 10);

        executeAndSerialize(args);

        verify(debugContext).waitForSuspendOrTerminate(eq(10), any());
    }
}
