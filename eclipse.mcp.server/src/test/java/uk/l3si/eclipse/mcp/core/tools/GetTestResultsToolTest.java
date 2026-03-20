package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.ILaunch;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import uk.l3si.eclipse.mcp.tools.ProgressReporter;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GetTestResultsToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(GetTestResultsTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    /** Stub DebugPlugin so checkNoActiveDebugLaunch() passes (no launches). */
    private MockedStatic<DebugPlugin> mockDebugPlugin() {
        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class);
        mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);
        return mocked;
    }

    @Test
    void nameIsGetTestResults() {
        assertEquals("get_test_results", new GetTestResultsTool().getName());
    }

    @Test
    void methodWithoutClassThrows() {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin()) {
            GetTestResultsTool tool = new GetTestResultsTool();
            JsonObject args = new JsonObject();
            args.addProperty("method", "testSomething");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("'class' is required"));
        }
    }

    @Test
    void fullTraceMode() throws Exception {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin();
             MockedStatic<TestResultsHelper> trh = mockStatic(TestResultsHelper.class)) {

            trh.when(() -> TestResultsHelper.getFailureTrace("com.example.FooTest", "testBar"))
                    .thenReturn("java.lang.AssertionError: expected true\n\tat com.example.FooTest.testBar(FooTest.java:10)");

            GetTestResultsTool tool = new GetTestResultsTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.FooTest");
            args.addProperty("method", "testBar");

            JsonObject result = executeAndSerialize(tool, args);
            assertEquals("com.example.FooTest", result.get("class").getAsString());
            assertEquals("testBar", result.get("method").getAsString());
            assertTrue(result.get("trace").getAsString().contains("AssertionError"));
        }
    }

    @Test
    void fullTraceModeNotFound() {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin();
             MockedStatic<TestResultsHelper> trh = mockStatic(TestResultsHelper.class)) {

            trh.when(() -> TestResultsHelper.getFailureTrace("com.example.FooTest", "testBar"))
                    .thenReturn(null);

            GetTestResultsTool tool = new GetTestResultsTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.FooTest");
            args.addProperty("method", "testBar");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("No failure trace found"));
            assertTrue(ex.getMessage().contains("com.example.FooTest#testBar"));
        }
    }

    @Test
    void summaryMode() throws Exception {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin();
             MockedStatic<TestResultsHelper> trh = mockStatic(TestResultsHelper.class)) {

            TestRunResult mockResult = TestRunResult.builder()
                    .status("COMPLETED")
                    .totalTests(5)
                    .passed(4)
                    .failed(1)
                    .errors(0)
                    .ignored(0)
                    .failures(Collections.emptyList())
                    .build();

            trh.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(mockResult);

            GetTestResultsTool tool = new GetTestResultsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            assertEquals("COMPLETED", result.get("status").getAsString());
            assertEquals(5, result.get("totalTests").getAsInt());
            assertEquals(4, result.get("passed").getAsInt());
            assertEquals(1, result.get("failed").getAsInt());
        }
    }

    @Test
    void summaryModeNoResults() {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin();
             MockedStatic<TestResultsHelper> trh = mockStatic(TestResultsHelper.class)) {

            trh.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(null);

            GetTestResultsTool tool = new GetTestResultsTool();

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tool.execute(new Args(new JsonObject()), message -> {}));
            assertTrue(ex.getMessage().contains("No test runs found"));
        }
    }

    @Test
    void classOnlyReturnsSummary() throws Exception {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin();
             MockedStatic<TestResultsHelper> trh = mockStatic(TestResultsHelper.class)) {

            TestRunResult mockResult = TestRunResult.builder()
                    .status("COMPLETED")
                    .totalTests(3)
                    .passed(3)
                    .failed(0)
                    .errors(0)
                    .ignored(0)
                    .failures(Collections.emptyList())
                    .build();

            trh.when(() -> TestResultsHelper.collect(eq(false), any(ProgressReporter.class))).thenReturn(mockResult);

            GetTestResultsTool tool = new GetTestResultsTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.FooTest");

            JsonObject result = executeAndSerialize(tool, args);

            // Should return summary, not trace
            assertEquals("COMPLETED", result.get("status").getAsString());
            assertEquals(3, result.get("totalTests").getAsInt());
            assertNull(result.get("trace"));

            // getFailureTrace should NOT have been called
            trh.verify(() -> TestResultsHelper.getFailureTrace(anyString(), anyString()), never());
        }
    }
}
