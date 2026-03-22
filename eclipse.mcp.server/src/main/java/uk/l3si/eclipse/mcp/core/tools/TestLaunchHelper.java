package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.debugging.DebugContext;
import uk.l3si.eclipse.mcp.debugging.DebugContext.WaitResult;
import uk.l3si.eclipse.mcp.debugging.VariableCollector;
import uk.l3si.eclipse.mcp.model.LaunchTestResult;
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
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import uk.l3si.eclipse.mcp.tools.ProgressReporter;

import java.util.List;

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
     * Launch a test configuration with class/method overrides.
     * Creates an in-memory working copy (never saved) with the overridden test target,
     * launches it, waits for test results, and returns structured results.
     *
     * @param projectName if provided, overrides the project on the working copy;
     *                    if null, reads the project from the existing config
     */
    private static final int DEBUG_TIMEOUT_SECONDS = 300;

    public static LaunchTestResult launchTest(String configName, String className, List<String> methods, String projectName, String mode, DebugContext debugContext, ProgressReporter progress) throws Exception {
        ILaunchConfiguration config = findTestConfig(configName);

        // Resolve project: user-provided or from existing config
        String resolvedProject = projectName;
        if (resolvedProject == null) {
            resolvedProject = config.getAttribute(ATTR_PROJECT_NAME, (String) null);
        }

        // Validate test class and methods exist in the project
        if (resolvedProject != null) {
            validateTestClassExists(resolvedProject, className);
            if (methods != null) {
                for (String m : methods) {
                    validateTestMethodExists(resolvedProject, className, m);
                }
            }
        }

        // Create working copy with test target overrides
        ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
        wc.setAttribute(ATTR_MAIN_TYPE, className);
        if (resolvedProject != null) {
            wc.setAttribute(ATTR_PROJECT_NAME, resolvedProject);
        }
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

        // In debug mode, require at least one breakpoint and ensure they are not globally skipped
        if ("debug".equals(mode)) {
            var bpManager = DebugPlugin.getDefault().getBreakpointManager();
            if (bpManager.getBreakpoints().length == 0) {
                throw new IllegalStateException(
                        "No breakpoints set. Set at least one breakpoint with breakpoint action='set' before launching in debug mode.");
            }
            bpManager.setEnabled(true);
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
        LaunchTestResult.Builder builder = LaunchTestResult.builder()
                .config(configName)
                .project(resolvedProject)
                .className(className)
                .method(methods != null && methods.size() == 1 ? methods.get(0) : null)
                .methods(methods);

        // In debug mode, wait for breakpoint hit or termination
        if ("debug".equals(mode)) {
            WaitResult wait = debugContext.waitForSuspendOrTerminate(DEBUG_TIMEOUT_SECONDS, progress);
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
        IType type = javaProject.findType(className);
        if (type == null || !type.exists()) {
            throw new IllegalArgumentException(
                    "Test class '" + className + "' not found in project '" + projectName + "'. "
                    + "Check that the fully qualified class name is correct and the project has been built.");
        }
    }

    /**
     * Validate that a test method exists in the given test class.
     */
    private static void validateTestMethodExists(String projectName, String className, String methodName) throws Exception {
        IJavaProject javaProject = JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject(projectName));
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
