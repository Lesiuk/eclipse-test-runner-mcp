package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.VariableCollector;
import uk.l3si.eclipse.mcp.model.LaunchTestResult;
import uk.l3si.eclipse.mcp.model.TestFailureInfo;
import uk.l3si.eclipse.mcp.model.TestRunResult;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.eclemma.core.CoverageTools;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import uk.l3si.eclipse.mcp.tools.ProgressReporter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("restriction")
public class TestLaunchHelper {

    /**
     * Check that no JUnit test is currently running.
     * Throws if a test is genuinely in progress, suggesting to use 'terminate' first.
     */
    static void checkNoTestRunning() throws Exception {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();

        for (ILaunch launch : manager.getLaunches()) {
            if (launch.getLaunchConfiguration() == null || !isJUnitConfig(launch.getLaunchConfiguration())) {
                continue;
            }
            if (!launch.isTerminated()) {
                throw new IllegalStateException(
                        "A test is already running: '" + launch.getLaunchConfiguration().getName() + "'. "
                        + "Use 'terminate' to stop it before launching a new test.");
            }
        }
    }

    private static final long RUNNING_CHECK_POLL_MS = 100;

    /**
     * Ensure no JUnit test is running, waiting up to {@code gracePeriodMs} for any launch that is
     * being terminated (e.g. by a concurrent {@code terminate} call) to finish terminating before
     * giving up.
     * <p>
     * This closes the race where {@code terminate} and {@code run_test} are dispatched back-to-back
     * and run concurrently on the server: {@code run_test} would otherwise observe a launch that is
     * still mid-termination and fail with "test already running" even though {@code terminate} is
     * actively stopping it. By waiting briefly, the in-flight termination completes and the new run
     * proceeds instead of failing.
     * <p>
     * Throws if a test is still running once the grace period elapses.
     */
    static void ensureNoTestRunning(long gracePeriodMs) throws Exception {
        long deadline = System.currentTimeMillis() + gracePeriodMs;
        while (true) {
            ILaunch running = findRunningJUnitLaunch();
            if (running == null) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException(
                        "A test is already running: '" + running.getLaunchConfiguration().getName() + "'. "
                        + "Use 'terminate' to stop it before launching a new test.");
            }
            Thread.sleep(RUNNING_CHECK_POLL_MS);
        }
    }

    /**
     * @return the first non-terminated JUnit launch, or {@code null} if none is running.
     */
    private static ILaunch findRunningJUnitLaunch() {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunch launch : manager.getLaunches()) {
            if (launch.getLaunchConfiguration() == null || !isJUnitConfig(launch.getLaunchConfiguration())) {
                continue;
            }
            if (!launch.isTerminated()) {
                return launch;
            }
        }
        return null;
    }

    /**
     * Find a launch configuration by name.
     */
    public static ILaunchConfiguration findConfig(String name) throws Exception {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunchConfiguration config : manager.getLaunchConfigurations()) {
            if (config.getName().equals(name)) {
                return config;
            }
        }
        return null;
    }

    /**
     * Find a launch configuration by name and validate it's a JUnit type.
     */
    public static ILaunchConfiguration findTestConfig(String configName) throws Exception {
        ILaunchConfiguration config = findConfig(configName);
        if (config == null) {
            throw new IllegalArgumentException("Launch configuration not found: " + configName);
        }
        String typeId = config.getType().getIdentifier();
        if (typeId == null || !typeId.toLowerCase().contains("junit")) {
            throw new IllegalArgumentException(
                    "Not a JUnit launch configuration: " + configName + " (type: " + config.getType().getName() + ")");
        }
        return config;
    }

    /**
     * Check whether a launch configuration is a JUnit type.
     */
    public static boolean isJUnitConfig(ILaunchConfiguration config) {
        try {
            String typeId = config.getType().getIdentifier();
            return typeId != null && typeId.toLowerCase().contains("junit");
        } catch (Exception e) {
            return false;
        }
    }


    private static final String ATTR_PROJECT_NAME = "org.eclipse.jdt.launching.PROJECT_ATTR";
    private static final String ATTR_MAIN_TYPE = "org.eclipse.jdt.launching.MAIN_TYPE";
    private static final String ATTR_TEST_NAME = "org.eclipse.jdt.junit.TESTNAME";
    private static final String ATTR_CONTAINER = "org.eclipse.jdt.junit.CONTAINER";
    private static final String ATTR_VM_ARGUMENTS = "org.eclipse.jdt.launching.VM_ARGUMENTS";

    /**
     * Returns true if the methods list represents a multi-method launch (more than one method).
     */
    static boolean isMultiMethod(List<String> methods) {
        return methods != null && methods.size() > 1;
    }

    /**
     * Build VM arguments string for multi-method mode.
     * Adds the javaagent and eclipse.mcp.test.methods system property,
     * preserving any existing VM arguments.
     */
    static String buildMultiMethodVmArgs(String agentJarPath, List<String> methods, String existing) {
        String agentArg = "-javaagent:" + agentJarPath;
        String methodsArg = "-Declipse.mcp.test.methods=" + String.join(",", methods);
        StringBuilder sb = new StringBuilder();
        if (existing != null && !existing.isBlank()) {
            sb.append(existing).append(' ');
        }
        sb.append(agentArg).append(' ').append(methodsArg);
        return sb.toString();
    }

    /**
     * Launch a test configuration with class/method or package overrides.
     * Creates an in-memory working copy (never saved) with the overridden test target,
     * launches it, waits for test results, and returns structured results.
     *
     * @param projectName if provided, overrides the project on the working copy;
     *                    if null, reads the project from the existing config
     */
    private static final int DEBUG_TIMEOUT_SECONDS = 300;

    /**
     * Backwards-compatible class-target overload for callers that predate package targets.
     */
    public static LaunchTestResult launchTest(String configName, String className, List<String> methods, String projectName, String mode, DebugContext debugContext, ProgressReporter progress) throws Exception {
        return launchTest(configName, className, null, methods, projectName, mode, debugContext, progress);
    }

    public static LaunchTestResult launchTest(String configName, String className, String packageName, List<String> methods, String projectName, String mode, DebugContext debugContext, ProgressReporter progress) throws Exception {
        if (className != null && packageName != null) {
            throw new IllegalArgumentException(
                    "Test targets 'class' and 'package' are mutually exclusive; provide only one.");
        }
        if (packageName != null && methods != null && !methods.isEmpty()) {
            throw new IllegalArgumentException(
                    "Package targets cannot be combined with 'method' or 'methods'; use a class target for method-level runs.");
        }
        ILaunchConfiguration config = findTestConfig(configName);

        // Resolve project: user-provided or from existing config
        String resolvedProject = projectName;
        if (resolvedProject == null) {
            resolvedProject = config.getAttribute(ATTR_PROJECT_NAME, (String) null);
        }

        // Validate the selected target exists in the project
        if (resolvedProject != null) {
            if (packageName != null) {
                IJavaProject javaProject = requireJavaProject(resolvedProject);
                List<IPackageFragment> testPackages = findTestPackages(javaProject, packageName);
                return launchPackageTests(config, resolvedProject, testPackages, mode, debugContext, progress);
            } else {
                validateTestClassExists(resolvedProject, className);
                if (methods != null) {
                    for (String m : methods) {
                        validateTestMethodExists(resolvedProject, className, m);
                    }
                }
            }
        } else if (packageName != null) {
            throw new IllegalArgumentException(
                    "Package target '" + packageName + "' requires a project; provide 'project' or use a launch configuration with a project.");
        }

        return launchSingleTest(config, className, null, methods, resolvedProject, mode, debugContext, progress);
    }

    private static LaunchTestResult launchPackageTests(ILaunchConfiguration config, String projectName,
            List<IPackageFragment> testPackages, String mode, DebugContext debugContext,
            ProgressReporter progress) throws Exception {
        if ("debug".equals(mode) && testPackages.size() > 1) {
            throw new IllegalArgumentException(
                    "Recursive package targets are not supported in debug mode; use a class target for debugging.");
        }

        List<LaunchTestResult> packageResults = new ArrayList<>();
        for (int i = 0; i < testPackages.size(); i++) {
            IPackageFragment testPackage = testPackages.get(i);
            progress.report("Launching package " + testPackage.getElementName()
                    + " (" + (i + 1) + "/" + testPackages.size() + ")...");
            packageResults.add(launchSingleTest(config, null, testPackage.getHandleIdentifier(), null,
                    projectName, mode, debugContext, progress));
        }
        return aggregatePackageResults(packageResults);
    }

    static LaunchTestResult aggregatePackageResults(List<LaunchTestResult> packageResults) {
        if (packageResults != null && packageResults.size() == 1 && packageResults.get(0) != null) {
            return packageResults.get(0);
        }

        List<TestRunResult> testResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> hints = new ArrayList<>();
        for (LaunchTestResult packageResult : packageResults) {
            if (packageResult == null) continue;
            if (packageResult.getTestResults() != null) {
                testResults.add(packageResult.getTestResults());
            }
            if (packageResult.getTestResultsError() != null && !packageResult.getTestResultsError().isBlank()) {
                errors.add(packageResult.getTestResultsError());
            }
            if (packageResult.getHint() != null && !packageResult.getHint().isBlank()) {
                hints.add(packageResult.getHint());
            }
        }

        LaunchTestResult.Builder builder = LaunchTestResult.builder();
        if (!testResults.isEmpty()) {
            builder.testResults(aggregateTestResults(testResults));
        }
        if (!errors.isEmpty()) {
            builder.testResultsError(String.join("\n", errors));
        }
        if (!hints.isEmpty()) {
            builder.hint(String.join("\n", hints));
        }
        return builder.build();
    }

    private static TestRunResult aggregateTestResults(List<TestRunResult> testResults) {
        String status = null;
        int totalTests = 0;
        int passed = 0;
        int failed = 0;
        int errors = 0;
        int ignored = 0;
        double elapsedSeconds = 0;
        boolean hasElapsed = false;
        List<TestFailureInfo> failures = new ArrayList<>();

        for (TestRunResult result : testResults) {
            if (result == null) continue;
            if (result.getStatus() != null) status = result.getStatus();
            totalTests += result.getTotalTests();
            passed += result.getPassed();
            failed += result.getFailed();
            errors += result.getErrors();
            ignored += result.getIgnored();
            if (result.getElapsedSeconds() != null) {
                elapsedSeconds += result.getElapsedSeconds();
                hasElapsed = true;
            }
            if (result.getFailures() != null) failures.addAll(result.getFailures());
        }

        TestRunResult.Builder builder = TestRunResult.builder()
                .status(status)
                .totalTests(totalTests)
                .passed(passed)
                .failed(failed)
                .errors(errors)
                .ignored(ignored)
                .failures(failures);
        if (hasElapsed) builder.elapsedSeconds(elapsedSeconds);
        return builder.build();
    }

    private static LaunchTestResult launchSingleTest(ILaunchConfiguration config, String className,
            String packageHandle, List<String> methods, String resolvedProject, String mode,
            DebugContext debugContext, ProgressReporter progress) throws Exception {
        // Create working copy with test target overrides
        ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
        if (resolvedProject != null) {
            wc.setAttribute(ATTR_PROJECT_NAME, resolvedProject);
        }
        if (packageHandle != null) {
            configurePackageTarget(wc, packageHandle);
        } else {
            wc.setAttribute(ATTR_MAIN_TYPE, className);
            if (methods != null && methods.size() == 1) {
                // Single method — use standard JUnit test name
                wc.setAttribute(ATTR_TEST_NAME, methods.get(0));
            } else if (isMultiMethod(methods)) {
                // Multi-method — run whole class, inject agent via VM args
                wc.setAttribute(ATTR_TEST_NAME, "");
                String agentPath = AgentJarLocator.getAgentJarPath();
                String existingVmArgs = config.getAttribute(ATTR_VM_ARGUMENTS, (String) null);
                wc.setAttribute(ATTR_VM_ARGUMENTS, buildMultiMethodVmArgs(agentPath, methods, existingVmArgs));
            } else {
                // No methods — run all tests in the class
                wc.setAttribute(ATTR_TEST_NAME, "");
            }
            // Clear container — running a specific class, not a package/project
            wc.removeAttribute(ATTR_CONTAINER);
        }

        // In debug mode, require at least one breakpoint and ensure they are not globally skipped
        if ("debug".equals(mode)) {
            var bpManager = DebugPlugin.getDefault().getBreakpointManager();
            if (bpManager.getBreakpoints().length == 0) {
                throw new IllegalStateException(
                        "No breakpoints set. Set at least one breakpoint with breakpoint action='set' before launching in debug mode.");
            }
            bpManager.setEnabled(true);
        }

        // Clear stale debug state before launching so that
        // waitForSuspendOrTerminate does not see leftover references
        // from a previous session.
        if ("debug".equals(mode)) {
            debugContext.reset();
        }

        // Launch on UI thread
        final ILaunch[] launchResult = new ILaunch[1];
        final Exception[] error = new Exception[1];

        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> {
            try {
                String launchMode = resolveLaunchMode(mode);
                launchResult[0] = wc.launch(launchMode, null);
            } catch (Exception e) {
                error[0] = e;
            }
        });

        if (error[0] != null) {
            throw error[0];
        }

        // Build result
        LaunchTestResult.Builder builder = LaunchTestResult.builder();

        // In debug mode, wait for breakpoint hit or termination
        if ("debug".equals(mode)) {
            WaitResult wait = debugContext.waitForSuspendOrTerminate(DEBUG_TIMEOUT_SECONDS, progress, launchResult[0], true);
            switch (wait) {
                case SUSPENDED -> builder
                        .debugStopped(true)
                        .debugReason(debugContext.getSuspendReason())
                        .debugLocation(debugContext.getCurrentLocation())
                        .debugVariables(VariableCollector.collectForCurrentFrame(debugContext));
                case TERMINATED -> {
                    builder.debugStopped(true)
                           .debugReason("terminated");
                    try {
                        TestRunResult testResults = TestResultsHelper.waitAndCollect(launchResult[0], progress);
                        if (testResults != null) {
                            builder.testResults(testResults);
                            if (testResults.getTotalTests() == 0) {
                                builder.hint("No tests were executed. This usually indicates a runtime error (e.g. class loading failure, missing dependency). Use 'get_console_output' to check for errors in the test runner output.");
                            }
                        } else {
                            builder.hint("No test session was created. The test runner may have failed to start. Use 'get_console_output' to check for errors.");
                        }
                    } catch (Exception e) {
                        builder.testResultsError("Failed to collect test results: " + e.getMessage());
                    }
                }
                case TIMEOUT -> builder
                        .debugStopped(false)
                        .debugReason("timeout");
            }
            return builder.build();
        }

        // Wait for test results
        try {
            TestRunResult testResults = TestResultsHelper.waitAndCollect(launchResult[0], progress);
            if (testResults != null) {
                builder.testResults(testResults);
                if (testResults.getTotalTests() == 0) {
                    builder.hint("No tests were executed. This usually indicates a runtime error (e.g. class loading failure, missing dependency). Use 'get_console_output' to check for errors in the test runner output.");
                }
            } else {
                builder.hint("No test session was created. The test runner may have failed to start. Use 'get_console_output' to check for errors.");
            }
        } catch (Exception e) {
            builder.testResultsError("Failed to collect test results: " + e.getMessage());
        } finally {
            cleanupLaunch(launchResult[0]);
        }

        return builder.build();
    }

    private static String resolveLaunchMode(String mode) {
        if (mode == null) return ILaunchManager.RUN_MODE;
        return switch (mode) {
            case "run" -> ILaunchManager.RUN_MODE;
            case "coverage" -> CoverageTools.LAUNCH_MODE;
            default -> mode; // plugin-registered modes (e.g. "debug") use their name as the Eclipse launch mode ID
        };
    }

    /**
     * Validate that a test class exists in the given project.
     * Gives a clear error message instead of Eclipse's cryptic
     * "The input type of the launch configuration does not exist".
     */
    private static void validateTestClassExists(String projectName, String className) throws Exception {
        IJavaProject javaProject = requireJavaProject(projectName);
        IType type = javaProject.findType(className);
        if (type == null || !type.exists()) {
            throw new IllegalArgumentException(
                    "Test class '" + className + "' not found in project '" + projectName + "'. "
                    + "Check that the fully qualified class name is correct and the project has been built.");
        }
    }

    /**
     * Resolve a Java project and provide the same clear validation used by class targets.
     */
    private static IJavaProject requireJavaProject(String projectName) throws Exception {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists()) {
            throw new IllegalArgumentException(ProjectBuilder.projectNotFoundMessage(projectName));
        }
        if (!project.isOpen()) {
            throw new IllegalArgumentException("Project is closed: " + projectName);
        }
        IJavaProject javaProject = JavaCore.create(project);
        if (javaProject == null || !javaProject.exists()) {
            throw new IllegalArgumentException("Not a Java project: " + projectName);
        }
        return javaProject;
    }

    private static IPackageFragment validateTestPackageExists(String projectName, String packageName) throws Exception {
        try {
            return findTestPackage(requireJavaProject(projectName), packageName);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("was not found")) {
                throw new IllegalArgumentException(
                        "Test package '" + packageName + "' not found in project '" + projectName + "'. "
                        + "Check that the fully qualified package name is correct and the project has been built.", e);
            }
            throw e;
        }
    }

    /**
     * Validate that a test method exists in the given test class.
     */
    private static void validateTestMethodExists(String projectName, String className, String methodName) throws Exception {
        IJavaProject javaProject = requireJavaProject(projectName);
        IType type = javaProject.findType(className);
        validateMethodOnType(type, className, methodName);
    }

    /**
     * Check that the given method name exists on the type. Package-visible for testing.
     */
    static void validateMethodOnType(IType type, String className, String methodName) throws Exception {
        IMethod[] methods = type.getMethods();
        if (methods.length == 0) {
            return; // can't validate — methods not resolved (e.g. binary type without source)
        }
        for (IMethod method : methods) {
            if (method.getElementName().equals(methodName)) {
                return;
            }
        }
        throw new IllegalArgumentException(
                "Test method '" + methodName + "' not found in class '" + className + "'. "
                + "Available methods: " + getMethodNames(type));
    }

    /**
     * Find a package in one of the project's source roots for use as a JUnit container target.
     */
    static IPackageFragment findTestPackage(IJavaProject javaProject, String packageName) throws Exception {
        IPackageFragment firstExisting = null;
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            if (root.getKind() != IPackageFragmentRoot.K_SOURCE) {
                continue;
            }
            IPackageFragment packageFragment = root.getPackageFragment(packageName);
            if (packageFragment != null && packageFragment.exists()) {
                if (firstExisting == null) {
                    firstExisting = packageFragment;
                }
                if (containsJUnitTests(packageFragment)) {
                    return packageFragment;
                }
            }
        }
        if (firstExisting != null) {
            return firstExisting;
        }
        throw new IllegalArgumentException(
                "Test package '" + packageName + "' was not found in a source root.");
    }

    /**
     * Find every source package at or below the requested package that has direct JUnit tests.
     * PDE's modern JUnit Plug-in Test launcher treats a package container as non-recursive, so
     * callers launch these exact package fragments one at a time and aggregate the results.
     */
    static List<IPackageFragment> findTestPackages(IJavaProject javaProject, String packageName) throws Exception {
        Map<String, IPackageFragment> candidates = new LinkedHashMap<>();
        for (IPackageFragmentRoot root : javaProject.getPackageFragmentRoots()) {
            if (root.getKind() != IPackageFragmentRoot.K_SOURCE) continue;

            addPackageCandidate(candidates, root.getPackageFragment(packageName), packageName);
            for (var child : root.getChildren()) {
                if (child instanceof IPackageFragment packageFragment) {
                    addPackageCandidate(candidates, packageFragment, packageName);
                }
            }
        }

        List<IPackageFragment> testPackages = candidates.values().stream()
                .filter(TestLaunchHelper::containsDirectJUnitTests)
                .sorted(Comparator.comparing(IPackageFragment::getElementName)
                        .thenComparing(IPackageFragment::getHandleIdentifier))
                .toList();
        if (testPackages.isEmpty()) {
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "Test package '" + packageName + "' was not found in a source root.");
            }
            throw new IllegalArgumentException(
                    "No JUnit tests found in package '" + packageName + "' or its subpackages.");
        }
        return testPackages;
    }

    private static void addPackageCandidate(Map<String, IPackageFragment> candidates,
            IPackageFragment packageFragment, String requestedPackage) {
        if (packageFragment == null || !packageFragment.exists()) return;
        String name = packageFragment.getElementName();
        boolean matches = name.equals(requestedPackage)
                || (!requestedPackage.isEmpty() && name.startsWith(requestedPackage + "."));
        if (matches) candidates.putIfAbsent(packageFragment.getHandleIdentifier(), packageFragment);
    }

    private static boolean containsDirectJUnitTests(IPackageFragment packageFragment) {
        try {
            String packageHandle = packageFragment.getHandleIdentifier();
            for (IType testType : JUnitCore.findTestTypes(packageFragment, null)) {
                IPackageFragment declaringPackage = testType.getPackageFragment();
                if (declaringPackage != null && packageHandle.equals(declaringPackage.getHandleIdentifier())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Package discovery is best effort; the launch will report any authoritative runner error.
        }
        return false;
    }

    private static boolean containsJUnitTests(IPackageFragment packageFragment) {
        try {
            return JUnitCore.findTestTypes(packageFragment, null).length > 0;
        } catch (Exception e) {
            // Package discovery is a best-effort disambiguation when the same package exists
            // in multiple source roots. Let the launch provide the authoritative test result.
            return false;
        }
    }

    /**
     * Configure an in-memory launch for a package/container target.
     */
    static void configurePackageTarget(ILaunchConfigurationWorkingCopy workingCopy, String packageHandle) {
        workingCopy.setAttribute(ATTR_CONTAINER, packageHandle);
        workingCopy.setAttribute(ATTR_TEST_NAME, "");
        workingCopy.removeAttribute(ATTR_MAIN_TYPE);
    }

    private static String getMethodNames(IType type) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (IMethod method : type.getMethods()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(method.getElementName());
        }
        return sb.toString();
    }

    private static final long LAUNCH_CLEANUP_TIMEOUT_MS = 10_000;
    private static final long LAUNCH_CLEANUP_POLL_MS = 100;

    /**
     * Wait for a launch to fully terminate.
     * This closes the race window between JUnit session completion and ILaunch termination,
     * preventing checkNoTestRunning() from seeing a briefly-not-yet-terminated launch.
     * The launch is intentionally kept in the launch manager to preserve console output.
     */
    private static void cleanupLaunch(ILaunch launch) {
        try {
            long deadline = System.currentTimeMillis() + LAUNCH_CLEANUP_TIMEOUT_MS;
            while (!launch.isTerminated() && System.currentTimeMillis() < deadline) {
                Thread.sleep(LAUNCH_CLEANUP_POLL_MS);
            }
        } catch (Exception e) {
            // best effort cleanup
        }
    }
}
