package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.model.TestFailureInfo;
import uk.l3si.eclipse.mcp.model.TestRunResult;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.StackTraceFilter;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.JUnitModel;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.junit.model.ITestCaseElement;
import org.eclipse.jdt.junit.model.ITestElement;
import org.eclipse.jdt.junit.model.ITestElement.FailureTrace;
import org.eclipse.jdt.junit.model.ITestElement.ProgressState;
import org.eclipse.jdt.junit.model.ITestElement.Result;
import org.eclipse.jdt.junit.model.ITestElementContainer;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.junit.model.ITestSuiteElement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("restriction")
public class TestResultsHelper {

    private static final long POST_TERMINATION_GRACE_MS = 5 * 1000;
    private static final long POLL_INTERVAL_MS = 100;
    private static final long KEEPALIVE_INTERVAL_MS = 60_000;

    public static TestRunResult waitAndCollect(ILaunch launch, ProgressReporter progress) throws InterruptedException {
        JUnitModel model = JUnitCorePlugin.getModel();
        TestRunSession session = findSession(model, launch);
        if (session == null) {
            return null;
        }

        waitForCompletion(session, launch, progress);
        return buildResult(session);
    }

    public static TestRunResult collect(boolean wait, ProgressReporter progress) throws InterruptedException {
        JUnitModel model = JUnitCorePlugin.getModel();
        TestRunSession session;

        if (wait) {
            ILaunch runningLaunch = findRunningJUnitLaunch();
            session = findSession(model, runningLaunch);
            if (session == null) {
                return null;
            }
            ILaunch sessionLaunch = null;
            try { sessionLaunch = session.getLaunch(); } catch (Exception e) { /* best effort */ }
            waitForCompletion(session, sessionLaunch != null ? sessionLaunch : runningLaunch, progress);
        } else {
            List<TestRunSession> sessions = model.getTestRunSessions();
            session = sessions.isEmpty() ? null : sessions.get(0);
        }

        if (session == null) {
            return null;
        }
        return buildResult(session);
    }

    private static ILaunch findRunningJUnitLaunch() {
        try {
            ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
            for (ILaunch launch : manager.getLaunches()) {
                if (!launch.isTerminated()
                        && launch.getLaunchConfiguration() != null
                        && TestLaunchHelper.isJUnitConfig(launch.getLaunchConfiguration())) {
                    return launch;
                }
            }
        } catch (Exception e) {
            // best effort
        }
        return null;
    }

    private static void waitForCompletion(TestRunSession session, ILaunch launch,
            ProgressReporter progress) throws InterruptedException {
        if (launch == null) return;

        Set<String> reported = new HashSet<>();
        long terminatedAt = -1;
        long lastProgressTime = System.currentTimeMillis();
        while (isStillRunning(session)) {
            int beforeSize = reported.size();
            reportNewTestResults(session, reported, progress);
            if (reported.size() > beforeSize) {
                lastProgressTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lastProgressTime >= KEEPALIVE_INTERVAL_MS) {
                progress.report("Waiting for test to complete...");
                lastProgressTime = System.currentTimeMillis();
            }
            if (!launch.isTerminated()) {
                // keep waiting
            } else {
                if (terminatedAt < 0) terminatedAt = System.currentTimeMillis();
                if (System.currentTimeMillis() - terminatedAt > POST_TERMINATION_GRACE_MS) {
                    break;
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        reportNewTestResults(session, reported, progress);
    }

    private static TestRunResult buildResult(TestRunSession session) {
        TestRunResult.Builder builder = TestRunResult.builder();

        ProgressState state = session.getProgressState();
        builder.status(state.toString());

        int[] stats = {0, 0, 0, 0, 0}; // total, passed, failed, errors, ignored
        List<TestFailureInfo> failures = new ArrayList<>();
        collectResults(session, failures, stats);

        builder.totalTests(stats[0]);
        builder.passed(stats[1]);
        builder.failed(stats[2]);
        builder.errors(stats[3]);
        builder.ignored(stats[4]);

        double elapsed = session.getElapsedTimeInSeconds();
        if (!Double.isNaN(elapsed)) {
            builder.elapsedSeconds(Math.round(elapsed * 100.0) / 100.0);
        }

        builder.failures(failures);

        return builder.build();
    }

    private static TestRunSession findSession(JUnitModel model, ILaunch launch) throws InterruptedException {
        List<TestRunSession> sessions = model.getTestRunSessions();
        TestRunSession existing = sessions.isEmpty() ? null : sessions.get(0);

        if (existing != null && isStillRunning(existing)) {
            return existing;
        }

        if (launch == null) {
            return existing;
        }

        long terminatedAt = -1;

        while (true) {
            sessions = model.getTestRunSessions();
            if (!sessions.isEmpty()) {
                TestRunSession latest = sessions.get(0);
                if (latest != existing || isStillRunning(latest)) {
                    return latest;
                }
            }

            if (!launch.isTerminated()) {
                Thread.sleep(POLL_INTERVAL_MS);
                continue;
            }

            if (terminatedAt < 0) terminatedAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - terminatedAt > POST_TERMINATION_GRACE_MS) {
                break;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }

        return existing;
    }

    private static boolean isStillRunning(ITestRunSession session) {
        ProgressState state = session.getProgressState();
        return state == ProgressState.RUNNING || state == ProgressState.NOT_STARTED;
    }

    private static void reportNewTestResults(ITestRunSession session, Set<String> reported,
            ProgressReporter progress) {
        reportNewTestResults((ITestElementContainer) session, reported, progress);
    }

    static void reportNewTestResults(ITestElementContainer container, Set<String> reported,
            ProgressReporter progress) {
        for (ITestElement child : container.getChildren()) {
            if (child instanceof ITestCaseElement testCase) {
                String key = testCase.getTestClassName() + "#" + testCase.getTestMethodName();
                if (reported.contains(key)) continue;
                Result result = testCase.getTestResult(false);
                if (result == Result.UNDEFINED) continue;
                reported.add(key);
                progress.report(formatTestProgress(testCase, result));
            } else if (child instanceof ITestElementContainer nested) {
                reportNewTestResults(nested, reported, progress);
            }
        }
        // Report suite-level failures (e.g. @BeforeClass exceptions)
        if (container instanceof ITestSuiteElement suite) {
            String key = suite.getSuiteTypeName() + "#<classSetup>";
            if (!reported.contains(key)) {
                FailureTrace trace = suite.getFailureTrace();
                if (trace != null && trace.getTrace() != null) {
                    reported.add(key);
                    String firstLine = trace.getTrace().split("\n")[0].trim();
                    progress.report("ERROR: " + suite.getSuiteTypeName() + " class setup — " + firstLine);
                }
            }
        }
    }

    static String formatTestProgress(ITestCaseElement testCase, Result result) {
        String method = testCase.getTestMethodName();
        double elapsed = testCase.getElapsedTimeInSeconds();
        String time = Double.isNaN(elapsed) ? "" : " (" + Math.round(elapsed * 10.0) / 10.0 + "s)";
        if (result == Result.OK) {
            return "PASSED: " + method + time;
        } else if (result == Result.IGNORED) {
            return "SKIPPED: " + method;
        }
        String prefix = result == Result.ERROR ? "ERROR: " : "FAILED: ";
        FailureTrace trace = testCase.getFailureTrace();
        if (trace != null && trace.getTrace() != null) {
            String firstLine = trace.getTrace().split("\n")[0].trim();
            return prefix + method + " — " + firstLine;
        }
        return prefix + method + time;
    }

    static void collectResults(ITestElement element, List<TestFailureInfo> failures, int[] stats) {
        if (element instanceof ITestCaseElement testCase) {
            stats[0]++;
            Result testResult = testCase.getTestResult(false);
            if (testResult == Result.OK) {
                stats[1]++;
            } else if (testResult == Result.FAILURE) {
                stats[2]++;
                addFailure(testCase, "FAILURE", failures);
            } else if (testResult == Result.ERROR) {
                stats[3]++;
                addFailure(testCase, "ERROR", failures);
            } else if (testResult == Result.IGNORED) {
                stats[4]++;
            }
        } else if (element instanceof ITestElementContainer container) {
            int failuresBefore = failures.size();
            ITestElement[] children = container.getChildren();
            if (children != null) {
                for (ITestElement child : children) {
                    collectResults(child, failures, stats);
                }
            }
            // Suite-level failures (e.g. @BeforeClass / @BeforeAll exceptions)
            if (failures.size() == failuresBefore
                    && element instanceof ITestSuiteElement suite) {
                FailureTrace trace = suite.getFailureTrace();
                if (trace != null) {
                    stats[0]++;
                    stats[3]++;
                    addSuiteFailure(suite, failures);
                }
            }
        }
    }

    private static void addFailure(ITestCaseElement testCase, String kind, List<TestFailureInfo> failures) {
        TestFailureInfo.Builder builder = TestFailureInfo.builder()
                .className(testCase.getTestClassName())
                .method(testCase.getTestMethodName())
                .kind(kind);

        FailureTrace trace = testCase.getFailureTrace();
        if (trace != null) {
            if (trace.getTrace() != null) builder.message(extractMessage(trace.getTrace(), testCase.getTestClassName()));
            if (trace.getExpected() != null) builder.expected(trace.getExpected());
            if (trace.getActual() != null) builder.actual(trace.getActual());
        }

        failures.add(builder.build());
    }

    private static void addSuiteFailure(ITestSuiteElement suite, List<TestFailureInfo> failures) {
        String className = suite.getSuiteTypeName();
        TestFailureInfo.Builder builder = TestFailureInfo.builder()
                .className(className)
                .method("<classSetup>")
                .kind("ERROR");

        FailureTrace trace = suite.getFailureTrace();
        if (trace != null) {
            if (trace.getTrace() != null) builder.message(extractMessage(trace.getTrace(), className));
            if (trace.getExpected() != null) builder.expected(trace.getExpected());
            if (trace.getActual() != null) builder.actual(trace.getActual());
        }

        failures.add(builder.build());
    }

    private static final int MAX_MESSAGE_FRAMES = 3;
    private static final String FRAME_PREFIX = "at ";

    static String extractMessage(String trace, String testClassName) {
        String testClassPrefix = FRAME_PREFIX + testClassName + ".";
        String[] lines = trace.split("\n");
        StringBuilder sb = new StringBuilder(lines[0].trim());
        int kept = 0;
        int omitted = 0;
        boolean hasFrames = false;
        for (int i = 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();

            // Keep non-frame lines: message continuations, Caused by, Suppressed
            if (!trimmed.startsWith(FRAME_PREFIX) && !trimmed.startsWith("...")) {
                if (omitted > 0 && hasFrames) {
                    sb.append("\n\t... ").append(omitted).append(" more");
                    omitted = 0;
                }
                // Reset kept-frame counter on new exception in the chain
                if (trimmed.startsWith("Caused by:") || trimmed.startsWith("Suppressed:")) {
                    kept = 0;
                }
                sb.append('\n').append(lines[i]);
                hasFrames = false;
                continue;
            }

            if (trimmed.startsWith(testClassPrefix)) {
                if (omitted > 0 && hasFrames) {
                    sb.append("\n\t... ").append(omitted).append(" more");
                }
                omitted = 0;
                sb.append('\n').append(lines[i]);
                hasFrames = true;
            } else if (kept < MAX_MESSAGE_FRAMES
                    && trimmed.startsWith(FRAME_PREFIX)
                    && !StackTraceFilter.isFrameworkFrame(trimmed.substring(FRAME_PREFIX.length()))) {
                if (omitted > 0 && hasFrames) {
                    sb.append("\n\t... ").append(omitted).append(" more");
                }
                omitted = 0;
                sb.append('\n').append(lines[i]);
                hasFrames = true;
                kept++;
            } else if (trimmed.startsWith(FRAME_PREFIX)) {
                omitted++;
            }
        }
        if (omitted > 0) {
            sb.append("\n\t... ").append(omitted).append(" more");
        }
        return sb.toString();
    }

    static String getFailureTrace(String className, String methodName) {
        JUnitModel model = JUnitCorePlugin.getModel();
        List<TestRunSession> sessions = model.getTestRunSessions();
        if (sessions.isEmpty()) return null;

        TestRunSession session = sessions.get(0);
        return findTrace(session, className, methodName);
    }

    static String findTrace(ITestElementContainer container, String className, String methodName) {
        // Check suite-level trace (e.g. @BeforeClass failures reported as <classSetup>)
        if (container instanceof ITestSuiteElement suite
                && suite.getSuiteTypeName().equals(className)
                && "<classSetup>".equals(methodName)) {
            FailureTrace trace = suite.getFailureTrace();
            if (trace != null) return trace.getTrace();
        }
        for (ITestElement child : container.getChildren()) {
            if (child instanceof ITestCaseElement testCase) {
                if (testCase.getTestClassName().equals(className)
                        && testCase.getTestMethodName().equals(methodName)) {
                    FailureTrace trace = testCase.getFailureTrace();
                    return trace != null ? trace.getTrace() : null;
                }
            } else if (child instanceof ITestElementContainer nested) {
                String result = findTrace(nested, className, methodName);
                if (result != null) return result;
            }
        }
        return null;
    }
}
