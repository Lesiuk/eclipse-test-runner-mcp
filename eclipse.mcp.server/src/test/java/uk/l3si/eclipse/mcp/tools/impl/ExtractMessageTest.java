package uk.l3si.eclipse.mcp.tools.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractMessageTest {

    private static final String TEST_CLASS = "com.example.FooTest";

    @Test
    void messageLineOnly() {
        String trace = "java.lang.AssertionError: expected 5 but was 3";
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        assertEquals("java.lang.AssertionError: expected 5 but was 3", result);
    }

    @Test
    void testClassFramesAlwaysIncluded() {
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat com.example.FooTest.testAdd(FooTest.java:42)",
                "\tat com.example.FooTest.helper(FooTest.java:10)",
                "\tat org.junit.Runner.run(Runner.java:1)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        assertTrue(result.contains("com.example.FooTest.testAdd"), "should include first test frame");
        assertTrue(result.contains("com.example.FooTest.helper"), "should include second test frame");
        assertFalse(result.contains("org.junit."), "should exclude JUnit frames");
    }

    @Test
    void testClassFramesDoNotCountAgainstLimit() {
        // 5 test-class frames + 4 non-framework frames → all test frames kept, only 3 non-framework kept
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat com.example.FooTest.a(FooTest.java:1)",
                "\tat com.example.FooTest.b(FooTest.java:2)",
                "\tat com.example.FooTest.c(FooTest.java:3)",
                "\tat com.example.FooTest.d(FooTest.java:4)",
                "\tat com.example.FooTest.e(FooTest.java:5)",
                "\tat com.example.Service.doWork(Service.java:10)",
                "\tat com.example.Repo.save(Repo.java:20)",
                "\tat com.example.Util.convert(Util.java:30)",
                "\tat com.example.Extra.thing(Extra.java:40)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        // All 5 test-class frames present
        for (char c = 'a'; c <= 'e'; c++) {
            assertTrue(result.contains("com.example.FooTest." + c), "should include test frame " + c);
        }
        // First 3 non-framework frames present
        assertTrue(result.contains("com.example.Service.doWork"), "should include 1st non-framework frame");
        assertTrue(result.contains("com.example.Repo.save"), "should include 2nd non-framework frame");
        assertTrue(result.contains("com.example.Util.convert"), "should include 3rd non-framework frame");
        // 4th non-framework frame omitted
        assertFalse(result.contains("com.example.Extra.thing"), "should omit 4th non-framework frame");
    }

    @Test
    void frameworkFramesFiltered() {
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat org.junit.Assert.fail(Assert.java:1)",
                "\tat org.eclipse.jdt.internal.junit.Runner.run(Runner.java:1)",
                "\tat java.base/java.lang.Thread.run(Thread.java:1)",
                "\tat com.example.FooTest.testIt(FooTest.java:5)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        assertFalse(result.contains("org.junit."), "should filter org.junit frames");
        assertFalse(result.contains("org.eclipse.jdt.internal.junit."), "should filter Eclipse JDT frames");
        assertFalse(result.contains("java.base/"), "should filter java.base frames");
        assertTrue(result.contains("com.example.FooTest.testIt"), "should keep test class frame");
    }

    @Test
    void omittedCountShownBetweenFrames() {
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat org.junit.Assert.fail(Assert.java:1)",
                "\tat org.junit.Runner.run(Runner.java:2)",
                "\tat com.example.FooTest.testIt(FooTest.java:5)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        assertTrue(result.contains("... 2 more"), "should show omitted count before test class frame");
    }

    @Test
    void omittedCountShownAtEnd() {
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat com.example.FooTest.testIt(FooTest.java:5)",
                "\tat com.example.A.a(A.java:1)",
                "\tat com.example.B.b(B.java:2)",
                "\tat com.example.C.c(C.java:3)",
                "\tat com.example.D.d(D.java:4)",
                "\tat com.example.E.e(E.java:5)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        // 3 kept (A, B, C), 2 omitted (D, E)
        assertTrue(result.contains("com.example.A.a"));
        assertTrue(result.contains("com.example.B.b"));
        assertTrue(result.contains("com.example.C.c"));
        assertFalse(result.contains("com.example.D.d"));
        assertTrue(result.contains("... 2 more"), "should show trailing omitted count");
    }

    @Test
    void noOmittedMarkerWhenNothingSkipped() {
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat com.example.FooTest.testIt(FooTest.java:5)",
                "\tat com.example.Service.run(Service.java:10)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        assertFalse(result.contains("..."), "should not show omitted marker when nothing skipped");
    }

    @Test
    void omittedCountBetweenNonFrameworkFrames() {
        // non-framework frame, then framework frames, then test-class frame
        String trace = String.join("\n",
                "java.lang.AssertionError: fail",
                "\tat com.example.Service.run(Service.java:10)",
                "\tat org.junit.Assert.fail(Assert.java:1)",
                "\tat org.junit.Runner.run(Runner.java:2)",
                "\tat java.base/java.lang.Thread.run(Thread.java:1)",
                "\tat com.example.FooTest.testIt(FooTest.java:5)");
        String result = TestResultsHelper.extractMessage(trace, TEST_CLASS);
        assertTrue(result.contains("com.example.Service.run"));
        assertTrue(result.contains("... 3 more"), "should show 3 omitted between service and test frames");
        assertTrue(result.contains("com.example.FooTest.testIt"));
    }
}
