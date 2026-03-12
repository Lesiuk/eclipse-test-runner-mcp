package uk.l3si.eclipse.mcp.tools.impl;

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

@SuppressWarnings("restriction")
public class TestLaunchHelper {

    /**
     * Check that no JUnit test is currently running.
     * Throws if a test is in progress, suggesting to use 'terminate' first.
     */
    static void checkNoTestRunning() throws Exception {
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
        for (ILaunch launch : manager.getLaunches()) {
            if (!launch.isTerminated()
                    && launch.getLaunchConfiguration() != null
                    && isJUnitConfig(launch.getLaunchConfiguration())) {
                String configName = launch.getLaunchConfiguration().getName();
                throw new IllegalStateException(
                        "A test is already running: '" + configName + "'. "
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

    /**
     * Launch a test configuration with class/method overrides.
     * Creates an in-memory working copy (never saved) with the overridden test target,
     * launches it, waits for test results, and returns structured results.
     *
     * @param projectName if provided, overrides the project on the working copy;
     *                    if null, reads the project from the existing config
     */
    public static LaunchTestResult launchTest(String configName, String className, String methodName, String projectName, String mode) throws Exception {
        ILaunchConfiguration config = findTestConfig(configName);

        // Resolve project: user-provided or from existing config
        String resolvedProject = projectName;
        if (resolvedProject == null) {
            resolvedProject = config.getAttribute(ATTR_PROJECT_NAME, (String) null);
        }

        // Validate test class and method exist in the project
        if (resolvedProject != null) {
            validateTestClassExists(resolvedProject, className);
            if (methodName != null) {
                validateTestMethodExists(resolvedProject, className, methodName);
            }
        }

        // Create working copy with test target overrides
        ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
        wc.setAttribute(ATTR_MAIN_TYPE, className);
        if (resolvedProject != null) {
            wc.setAttribute(ATTR_PROJECT_NAME, resolvedProject);
        }
        if (methodName != null) {
            wc.setAttribute(ATTR_TEST_NAME, methodName);
        } else {
            wc.setAttribute(ATTR_TEST_NAME, "");
        }
        // Clear container — running a specific class, not a package/project
        wc.removeAttribute(ATTR_CONTAINER);

        // In debug mode, require at least one breakpoint and ensure they are not globally skipped
        if ("debug".equals(mode)) {
            var bpManager = DebugPlugin.getDefault().getBreakpointManager();
            if (bpManager.getBreakpoints().length == 0) {
                throw new IllegalStateException(
                        "No breakpoints set. Set at least one breakpoint with 'set_breakpoint' before launching in debug mode.");
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
                .method(methodName);

        // In debug mode, return immediately — the debugger will suspend at breakpoints
        // and the LLM should use get_debug_state to check status
        if ("debug".equals(mode)) {
            builder.testResultsError("Launched in debug mode. Use 'get_debug_state' to check if a breakpoint was hit, "
                    + "then use debug tools (inspect_variable, evaluate_expression, step, resume) to interact with the debugger.");
            return builder.build();
        }

        // Wait for test results
        try {
            TestRunResult testResults = TestResultsHelper.waitAndCollect(launchResult[0]);
            if (testResults != null) {
                builder.testResults(testResults);
            }
        } catch (Exception e) {
            builder.testResultsError("Failed to collect test results: " + e.getMessage());
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
            throw new IllegalArgumentException("Project not found: " + projectName);
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
        for (IMethod method : type.getMethods()) {
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
}
