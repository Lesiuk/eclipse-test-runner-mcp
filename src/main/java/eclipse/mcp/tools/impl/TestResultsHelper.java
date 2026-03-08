package eclipse.mcp.tools.impl;

import eclipse.mcp.model.TestFailureInfo;
import eclipse.mcp.model.TestRunResult;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
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

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("restriction")
class TestResultsHelper {

    private static final long POST_TERMINATION_GRACE_MS = 5 * 1000;
    private static final long POLL_INTERVAL_MS = 500;

    static TestRunResult waitAndCollect(ILaunch launch) throws InterruptedException {
        JUnitModel model = JUnitCorePlugin.getModel();
        TestRunSession session = findSession(model, launch);
        if (session == null) {
            return null;
        }

        waitForCompletion(session, launch);
        return buildResult(session);
    }

    static TestRunResult collect(boolean wait) throws InterruptedException {
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
            waitForCompletion(session, sessionLaunch != null ? sessionLaunch : runningLaunch);
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

    private static void waitForCompletion(TestRunSession session, ILaunch launch) throws InterruptedException {
        if (launch == null) return;

        long terminatedAt = -1;
        while (isStillRunning(session)) {
            if (!launch.isTerminated()) {
                // Launch still alive — keep waiting
            } else {
                if (terminatedAt < 0) terminatedAt = System.currentTimeMillis();
                if (System.currentTimeMillis() - terminatedAt > POST_TERMINATION_GRACE_MS) {
                    break;
                }
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
    }

    private static TestRunResult buildResult(TestRunSession session) {
        TestRunResult.Builder builder = TestRunResult.builder();

        builder.testRunName(session.getTestRunName());
        try {
            ILaunchConfiguration launchConfig = session.getLaunch().getLaunchConfiguration();
            if (launchConfig != null) {
                builder.configName(launchConfig.getName());
            }
        } catch (Exception e) {
            // informational, don't fail
        }

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
            builder.elapsedSeconds(elapsed);
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
            Thread.sleep(POLL_INTERVAL_MS);

            sessions = model.getTestRunSessions();
            if (!sessions.isEmpty()) {
                TestRunSession latest = sessions.get(0);
                if (latest != existing || isStillRunning(latest)) {
                    return latest;
                }
            }

            if (!launch.isTerminated()) {
                continue;
            }

            if (terminatedAt < 0) terminatedAt = System.currentTimeMillis();
            if (System.currentTimeMillis() - terminatedAt > POST_TERMINATION_GRACE_MS) {
                break;
            }
        }

        return existing;
    }

    private static boolean isStillRunning(ITestRunSession session) {
        ProgressState state = session.getProgressState();
        return state == ProgressState.RUNNING || state == ProgressState.NOT_STARTED;
    }

    private static void collectResults(ITestElement element, List<TestFailureInfo> failures, int[] stats) {
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
            ITestElement[] children = container.getChildren();
            if (children != null) {
                for (ITestElement child : children) {
                    collectResults(child, failures, stats);
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

    private static final int MAX_MESSAGE_FRAMES = 3;
    private static final String FRAME_PREFIX = "at ";
    private static final String[] FRAMEWORK_PREFIXES = {
            "at org.junit.",
            "at org.eclipse.jdt.internal.junit.",
            "at java.base/"
    };

    static String extractMessage(String trace, String testClassName) {
        String testClassPrefix = FRAME_PREFIX + testClassName + ".";
        String[] lines = trace.split("\n");
        StringBuilder sb = new StringBuilder(lines[0].trim());
        int kept = 0;
        int omitted = 0;
        for (int i = 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith(testClassPrefix)) {
                if (omitted > 0) {
                    sb.append("\n\t... ").append(omitted).append(" more");
                    omitted = 0;
                }
                sb.append('\n').append(lines[i]);
            } else if (kept < MAX_MESSAGE_FRAMES
                    && trimmed.startsWith(FRAME_PREFIX)
                    && !isFrameworkFrame(trimmed)) {
                if (omitted > 0) {
                    sb.append("\n\t... ").append(omitted).append(" more");
                    omitted = 0;
                }
                sb.append('\n').append(lines[i]);
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

    private static boolean isFrameworkFrame(String trimmedLine) {
        for (String prefix : FRAMEWORK_PREFIXES) {
            if (trimmedLine.startsWith(prefix)) return true;
        }
        return false;
    }

    static String getFailureTrace(String className, String methodName) {
        JUnitModel model = JUnitCorePlugin.getModel();
        List<TestRunSession> sessions = model.getTestRunSessions();
        if (sessions.isEmpty()) return null;

        TestRunSession session = sessions.get(0);
        return findTrace(session, className, methodName);
    }

    private static String findTrace(ITestElementContainer container, String className, String methodName) {
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
