package uk.l3si.eclipse.mcp.core.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import uk.l3si.eclipse.mcp.model.TestFailureInfo;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestElement.FailureTrace;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.junit.model.ITestSuiteElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuiteFailureTest {

    private static final Gson GSON = new Gson();

    private static final String BEFORE_CLASS_TRACE =
            "java.lang.RuntimeException: DB connection failed\n"
                    + "\tat com.example.MyTest.setupClass(MyTest.java:15)\n"
                    + "\tat org.junit.Runner.run(Runner.java:1)";

    // --- collectResults ---

    @Test
    void collectResults_suiteWithBeforeClassFailure_reportsError() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        int[] stats = {0, 0, 0, 0, 0};
        List<TestFailureInfo> failures = new ArrayList<>();
        TestResultsHelper.collectResults(suite, failures, stats);

        assertEquals(1, stats[0], "total");
        assertEquals(0, stats[1], "passed");
        assertEquals(0, stats[2], "failed");
        assertEquals(1, stats[3], "errors");
        assertEquals(1, failures.size());
        JsonObject f = toJson(failures.get(0));
        assertEquals("com.example.MyTest", f.get("class").getAsString());
        assertEquals("<classSetup>", f.get("method").getAsString());
        assertEquals("ERROR", f.get("kind").getAsString());
        assertTrue(f.get("message").getAsString().contains("RuntimeException"));
    }

    @Test
    void collectResults_suiteWithBeforeClassFailure_andUndefinedChildren() {
        ITestCaseElement child1 = mockTestCase("com.example.MyTest", "testA", Result.UNDEFINED);
        ITestCaseElement child2 = mockTestCase("com.example.MyTest", "testB", Result.UNDEFINED);
        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[]{child1, child2});

        int[] stats = {0, 0, 0, 0, 0};
        List<TestFailureInfo> failures = new ArrayList<>();
        TestResultsHelper.collectResults(suite, failures, stats);

        // 2 UNDEFINED children counted in total + 1 suite error
        assertEquals(3, stats[0], "total");
        assertEquals(1, stats[3], "errors");
        assertEquals(1, failures.size());
        assertEquals("<classSetup>", toJson(failures.get(0)).get("method").getAsString());
    }

    @Test
    void collectResults_suiteWithChildErrors_doesNotDuplicateSuiteFailure() {
        ITestCaseElement child = mockTestCase("com.example.MyTest", "testA", Result.ERROR);
        FailureTrace childTrace = mockTrace("java.lang.NullPointerException\n\tat com.example.MyTest.testA(MyTest.java:30)");
        when(child.getFailureTrace()).thenReturn(childTrace);

        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[]{child});

        int[] stats = {0, 0, 0, 0, 0};
        List<TestFailureInfo> failures = new ArrayList<>();
        TestResultsHelper.collectResults(suite, failures, stats);

        // Only the child error, not the suite error
        assertEquals(1, stats[0], "total");
        assertEquals(1, stats[3], "errors");
        assertEquals(1, failures.size());
        assertEquals("testA", toJson(failures.get(0)).get("method").getAsString());
    }

    @Test
    void collectResults_suiteWithNoFailureTrace_doesNotAddError() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", null);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        int[] stats = {0, 0, 0, 0, 0};
        List<TestFailureInfo> failures = new ArrayList<>();
        TestResultsHelper.collectResults(suite, failures, stats);

        assertEquals(0, stats[0], "total");
        assertEquals(0, stats[3], "errors");
        assertTrue(failures.isEmpty());
    }

    @Test
    void collectResults_normalPassingTests_unchanged() {
        ITestCaseElement child = mockTestCase("com.example.MyTest", "testA", Result.OK);
        ITestSuiteElement suite = mockSuite("com.example.MyTest", null);
        when(suite.getChildren()).thenReturn(new ITestElement[]{child});

        int[] stats = {0, 0, 0, 0, 0};
        List<TestFailureInfo> failures = new ArrayList<>();
        TestResultsHelper.collectResults(suite, failures, stats);

        assertEquals(1, stats[0], "total");
        assertEquals(1, stats[1], "passed");
        assertTrue(failures.isEmpty());
    }

    // --- reportNewTestResults ---

    @Test
    void reportNewTestResults_suiteFailure_reported() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        Set<String> reported = new HashSet<>();
        List<String> messages = new ArrayList<>();
        TestResultsHelper.reportNewTestResults(suite, reported, messages::add);

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("com.example.MyTest"));
        assertTrue(messages.get(0).contains("class setup"));
        assertTrue(messages.get(0).startsWith("ERROR:"));
        assertTrue(reported.contains("com.example.MyTest#<classSetup>"));
    }

    @Test
    void reportNewTestResults_suiteFailure_notReportedTwice() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        Set<String> reported = new HashSet<>();
        reported.add("com.example.MyTest#<classSetup>");
        List<String> messages = new ArrayList<>();
        TestResultsHelper.reportNewTestResults(suite, reported, messages::add);

        assertTrue(messages.isEmpty());
    }

    @Test
    void reportNewTestResults_suiteWithNoTrace_notReported() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", null);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        Set<String> reported = new HashSet<>();
        List<String> messages = new ArrayList<>();
        TestResultsHelper.reportNewTestResults(suite, reported, messages::add);

        assertTrue(messages.isEmpty());
    }

    // --- findTrace ---

    @Test
    void findTrace_classSetup_returnsSuiteTrace() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        String trace = TestResultsHelper.findTrace(suite, "com.example.MyTest", "<classSetup>");

        assertEquals(BEFORE_CLASS_TRACE, trace);
    }

    @Test
    void findTrace_classSetup_wrongClass_returnsNull() {
        ITestSuiteElement suite = mockSuite("com.example.MyTest", BEFORE_CLASS_TRACE);
        when(suite.getChildren()).thenReturn(new ITestElement[0]);

        String trace = TestResultsHelper.findTrace(suite, "com.example.OtherTest", "<classSetup>");

        assertNull(trace);
    }

    @Test
    void findTrace_regularMethod_stillWorks() {
        ITestCaseElement testCase = mockTestCase("com.example.MyTest", "testFoo", Result.ERROR);
        FailureTrace caseTrace = mockTrace("java.lang.AssertionError: expected 1");
        when(testCase.getFailureTrace()).thenReturn(caseTrace);

        ITestSuiteElement suite = mockSuite("com.example.MyTest", null);
        when(suite.getChildren()).thenReturn(new ITestElement[]{testCase});

        String trace = TestResultsHelper.findTrace(suite, "com.example.MyTest", "testFoo");

        assertEquals("java.lang.AssertionError: expected 1", trace);
    }

    // --- helpers ---

    private static JsonObject toJson(TestFailureInfo info) {
        return GSON.toJsonTree(info).getAsJsonObject();
    }

    private static ITestSuiteElement mockSuite(String className, String traceText) {
        ITestSuiteElement suite = mock(ITestSuiteElement.class);
        when(suite.getSuiteTypeName()).thenReturn(className);
        if (traceText != null) {
            FailureTrace trace = mockTrace(traceText);
            when(suite.getFailureTrace()).thenReturn(trace);
            when(suite.getTestResult(false)).thenReturn(Result.ERROR);
        } else {
            when(suite.getTestResult(false)).thenReturn(Result.UNDEFINED);
        }
        return suite;
    }

    private static ITestCaseElement mockTestCase(String className, String method, Result result) {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestClassName()).thenReturn(className);
        when(testCase.getTestMethodName()).thenReturn(method);
        when(testCase.getTestResult(false)).thenReturn(result);
        when(testCase.getElapsedTimeInSeconds()).thenReturn(Double.NaN);
        return testCase;
    }

    private static FailureTrace mockTrace(String traceText) {
        FailureTrace trace = mock(FailureTrace.class);
        when(trace.getTrace()).thenReturn(traceText);
        return trace;
    }
}
