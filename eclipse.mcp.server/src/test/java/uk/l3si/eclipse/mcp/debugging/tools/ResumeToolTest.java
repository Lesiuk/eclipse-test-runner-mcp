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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeToolTest {

    private static final Gson GSON = new Gson();

    private DebugContext debugContext;
    private ResumeTool tool;

    @BeforeEach
    void setUp() {
        debugContext = mock(DebugContext.class);
        tool = new ResumeTool(debugContext);
    }

    private JsonObject executeAndSerialize(JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nameIsResume() {
        assertEquals("resume", tool.getName());
    }

    @Test
    void resumeAndHitBreakpoint() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");
        when(debugContext.getCurrentLocation()).thenReturn(
                LocationInfo.builder()
                        .className("com.example.App")
                        .method("doStuff")
                        .line(42)
                        .sourceName("App.java")
                        .build());

        JsonObject args = new JsonObject();
        args.addProperty("timeout", 2);
        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertTrue(result.get("stopped").getAsBoolean());
        assertEquals("breakpoint", result.get("reason").getAsString());

        JsonObject location = result.getAsJsonObject("location");
        assertEquals("com.example.App", location.get("class").getAsString());
        assertEquals("doStuff", location.get("method").getAsString());
        assertEquals(42, location.get("line").getAsInt());
    }

    @Test
    void resumeAndTerminate() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TERMINATED);

        JsonObject args = new JsonObject();
        args.addProperty("timeout", 2);
        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertTrue(result.get("stopped").getAsBoolean());
        assertEquals("terminated", result.get("reason").getAsString());
    }

    @Test
    void resumeAndTerminateWithTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TERMINATED);

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
            mocked.when(() -> TestResultsHelper.collect(false)).thenReturn(testRunResult);

            JsonObject args = new JsonObject();
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("stopped").getAsBoolean());
            assertEquals("terminated", result.get("reason").getAsString());

            JsonObject testResults = result.getAsJsonObject("testResults");
            assertNotNull(testResults, "testResults should be present when test finishes");
            assertEquals("COMPLETED", testResults.get("status").getAsString());
            assertEquals(3, testResults.get("totalTests").getAsInt());
            assertEquals(2, testResults.get("passed").getAsInt());
            assertEquals(1, testResults.get("failed").getAsInt());
            assertEquals(1.23, testResults.get("elapsedSeconds").getAsDouble(), 0.001);

            JsonArray failures = testResults.getAsJsonArray("failures");
            assertEquals(1, failures.size());
            assertEquals("com.example.FooTest", failures.get(0).getAsJsonObject().get("class").getAsString());
            assertEquals("testBar", failures.get(0).getAsJsonObject().get("method").getAsString());
        }
    }

    @Test
    void resumeAndTerminateWithNoTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TERMINATED);

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(false)).thenReturn(null);

            JsonObject args = new JsonObject();
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("stopped").getAsBoolean());
            assertEquals("terminated", result.get("reason").getAsString());
            assertFalse(result.has("testResults"), "testResults should be absent when no results available");
        }
    }

    @Test
    void resumeAndTerminateCollectionExceptionReturnsNullTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TERMINATED);

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            mocked.when(() -> TestResultsHelper.collect(false))
                    .thenThrow(new RuntimeException("JUnit model unavailable"));

            JsonObject args = new JsonObject();
            JsonObject result = executeAndSerialize(args);

            assertTrue(result.get("stopped").getAsBoolean());
            assertEquals("terminated", result.get("reason").getAsString());
            assertFalse(result.has("testResults"), "testResults should be absent on collection error");
        }
    }

    @Test
    void suspendedDoesNotCollectTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.SUSPENDED);
        when(debugContext.getSuspendReason()).thenReturn("breakpoint");

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            JsonObject args = new JsonObject();
            executeAndSerialize(args);

            mocked.verifyNoInteractions();
        }
    }

    @Test
    void timeoutDoesNotCollectTestResults() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);
        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TIMEOUT);

        try (MockedStatic<TestResultsHelper> mocked = mockStatic(TestResultsHelper.class)) {
            JsonObject args = new JsonObject();
            args.addProperty("timeout", 1);
            executeAndSerialize(args);

            mocked.verifyNoInteractions();
        }
    }

    @Test
    void resumeAndTimeout() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(true);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        when(debugContext.waitForSuspendOrTerminate(anyInt())).thenReturn(WaitResult.TIMEOUT);

        JsonObject args = new JsonObject();
        args.addProperty("timeout", 1);
        JsonObject result = executeAndSerialize(args);

        verify(thread).resume();
        assertFalse(result.get("stopped").getAsBoolean());
        assertEquals("timeout", result.get("reason").getAsString());
    }

    @Test
    void notSuspendedThrows() throws Exception {
        IJavaThread thread = mock(IJavaThread.class);
        when(thread.isSuspended()).thenReturn(false);
        when(thread.getName()).thenReturn("main");
        when(debugContext.resolveThread(null)).thenReturn(thread);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(null)));
        assertTrue(ex.getMessage().contains("not suspended"));
    }

    @Test
    void noSessionThrows() throws Exception {
        when(debugContext.resolveThread(null))
                .thenThrow(new IllegalStateException("No debug session active."));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tool.execute(new Args(null)));
        assertTrue(ex.getMessage().contains("No debug session"));
    }

    @Test
    void threadNotFoundThrows() throws Exception {
        when(debugContext.resolveThread(999L))
                .thenThrow(new IllegalArgumentException("Thread not found with ID: 999"));

        JsonObject args = new JsonObject();
        args.addProperty("thread_id", "999");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args)));
        assertTrue(ex.getMessage().contains("999"));
    }
}
