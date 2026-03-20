package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement.FailureTrace;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestResultsHelperProgressTest {

    @Test
    void formatPassedTest() {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestMethodName()).thenReturn("testLogin");
        when(testCase.getElapsedTimeInSeconds()).thenReturn(1.23);
        String result = TestResultsHelper.formatTestProgress(testCase, Result.OK);
        assertEquals("PASSED: testLogin (1.2s)", result);
    }

    @Test
    void formatFailedTest() {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestMethodName()).thenReturn("testLogout");
        when(testCase.getElapsedTimeInSeconds()).thenReturn(Double.NaN);
        FailureTrace trace = mock(FailureTrace.class);
        when(trace.getTrace()).thenReturn("AssertionError: expected 200\n\tat com.example.Test.run");
        when(testCase.getFailureTrace()).thenReturn(trace);
        String result = TestResultsHelper.formatTestProgress(testCase, Result.FAILURE);
        assertTrue(result.startsWith("FAILED: testLogout"));
        assertTrue(result.contains("AssertionError"));
    }

    @Test
    void formatSkippedTest() {
        ITestCaseElement testCase = mock(ITestCaseElement.class);
        when(testCase.getTestMethodName()).thenReturn("testDisabled");
        when(testCase.getElapsedTimeInSeconds()).thenReturn(Double.NaN);
        String result = TestResultsHelper.formatTestProgress(testCase, Result.IGNORED);
        assertEquals("SKIPPED: testDisabled", result);
    }
}
