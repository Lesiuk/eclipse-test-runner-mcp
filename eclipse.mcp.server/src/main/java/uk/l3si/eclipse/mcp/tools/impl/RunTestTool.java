package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.LaunchTestResult;
import uk.l3si.eclipse.mcp.model.ProblemInfo;
import uk.l3si.eclipse.mcp.model.RunTestResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunTestTool implements IMcpTool {

    private final Map<String, String> launchModes;

    public RunTestTool(Map<String, String> launchModes) {
        this.launchModes = launchModes;
    }

    @Override
    public String getName() {
        return "run_test";
    }

    @Override
    public String getDescription() {
        return "Refresh, build, then run a JUnit test. Use this after editing Java files externally — "
             + "it refreshes projects from the filesystem, rebuilds, checks for compilation errors, and then runs the test. "
             + "Requires an existing JUnit launch configuration (regular JUnit or JUnit Plug-in Test) which provides "
             + "all runtime settings (VM args, classpath, environment). Overrides the test target to run the specified class/method. "
             + "When source and tests live in different projects, use 'dependencies' to refresh and build dependency projects in order "
             + "(e.g. build 'mocks' before 'ui_tests'). "
             + "Fails if a test is already running — use 'terminate' to stop it first. "
             + "Waits for completion and returns test results.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("config", PropertySchema.string("Name of an existing JUnit launch configuration to use as template"))
                .property("class", PropertySchema.string("Fully qualified test class name (e.g. 'com.example.FooTest')"))
                .property("method", PropertySchema.string("Optional: specific test method name to run. If omitted, runs all tests in the class."))
                .property("project", PropertySchema.string("Project containing the test class. Sets the project on the launch config and is used for compilation error checking."))
                .property("dependencies", PropertySchema.array(
                        "Dependency projects that were modified externally and need refreshing/rebuilding before running tests. "
                        + "Only list projects where you changed files — not the full dependency graph. "
                        + "Built in order listed, then the test 'project' is refreshed and built last.",
                        PropertySchema.builder().type("string").build()
                ))
                .property("mode", PropertySchema.stringEnum(buildModeDescription(), new ArrayList<>(launchModes.keySet())))
                .required(List.of("config", "class"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String configName = args.requireString("config", "launch configuration name");
        String className = args.requireString("class", "fully qualified test class name");
        String methodName = args.getString("method");
        String projectName = args.getString("project");
        List<String> dependencies = args.getStringList("dependencies");
        String mode = args.getString("mode", "run");

        // Block if a test is already running
        TestLaunchHelper.checkNoTestRunning();

        // Validate config is JUnit before doing any work
        TestLaunchHelper.findTestConfig(configName);

        // Build the list of projects to refresh and build
        List<String> refreshProjects = resolveRefreshProjects(dependencies, projectName);

        // Refresh and build
        List<String> steps = refreshAndBuild(refreshProjects);

        // Check for compilation errors across all refreshed projects
        List<ProblemInfo> compilationErrors = checkCompilationErrors(refreshProjects);
        if (!compilationErrors.isEmpty()) {
            return RunTestResult.builder()
                    .steps(steps)
                    .success(false)
                    .errorCount(compilationErrors.size())
                    .compilationErrors(compilationErrors)
                    .build();
        }
        steps.add("No compilation errors");

        // Launch test
        LaunchTestResult launchResult = TestLaunchHelper.launchTest(configName, className, methodName, projectName, mode);
        return RunTestResult.builder()
                .steps(steps)
                .success(true)
                .launchResult(launchResult)
                .build();
    }

    private String buildModeDescription() {
        StringBuilder sb = new StringBuilder("Launch mode (default: 'run').");
        for (Map.Entry<String, String> entry : launchModes.entrySet()) {
            sb.append(" '").append(entry.getKey()).append("' — ").append(entry.getValue()).append(".");
        }
        return sb.toString();
    }

    /**
     * Resolve the list of projects to refresh and build.
     * Dependencies are built first (in order), then the test project is appended.
     */
    private List<String> resolveRefreshProjects(List<String> dependencies, String projectName) {
        if (dependencies != null && !dependencies.isEmpty()) {
            List<String> result = new ArrayList<>(dependencies);
            if (projectName != null && !result.contains(projectName)) {
                result.add(projectName);
            }
            return result;
        }
        if (projectName != null) {
            return List.of(projectName);
        }
        return null;
    }

    private List<String> refreshAndBuild(List<String> projectNames) throws Exception {
        List<String> steps = new ArrayList<>();
        final Exception[] jobError = new Exception[1];
        final List<String> jobSteps = new ArrayList<>();

        Job refreshAndBuild = new Job("MCP: Refresh & Build") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    SubMonitor sub = SubMonitor.convert(monitor, 100);

                    if (projectNames != null && !projectNames.isEmpty()) {
                        int projectCount = projectNames.size();
                        SubMonitor refreshMonitor = sub.split(40);
                        refreshMonitor.setWorkRemaining(projectCount);
                        SubMonitor buildMonitor = sub.split(50);
                        buildMonitor.setWorkRemaining(projectCount);

                        for (int i = 0; i < projectCount; i++) {
                            String name = projectNames.get(i);
                            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
                            if (!project.exists()) {
                                throw new IllegalArgumentException("Project not found: " + name);
                            }

                            refreshMonitor.subTask("Refreshing " + name + "...");
                            project.refreshLocal(IResource.DEPTH_INFINITE, refreshMonitor.split(1));
                            jobSteps.add("Refreshed project: " + name);

                            // Wait for auto-build after refresh
                            sub.subTask("Waiting for auto-build...");
                            Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);

                            // Build
                            buildMonitor.subTask("Building " + name + "...");
                            project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, buildMonitor.split(1));
                            jobSteps.add("Built project: " + name);
                        }
                    } else {
                        // No projects specified — refresh and build all open projects
                        IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
                        SubMonitor refreshMonitor = sub.split(30);
                        refreshMonitor.setWorkRemaining(allProjects.length);
                        for (IProject project : allProjects) {
                            if (project.isOpen()) {
                                refreshMonitor.subTask("Refreshing " + project.getName() + "...");
                                project.refreshLocal(IResource.DEPTH_INFINITE, refreshMonitor.split(1));
                            }
                        }
                        jobSteps.add("Refreshed all open projects");

                        sub.subTask("Waiting for auto-build...");
                        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, sub.split(10));

                        SubMonitor buildMonitor = sub.split(60);
                        buildMonitor.setWorkRemaining(allProjects.length);
                        for (IProject project : allProjects) {
                            if (project.isOpen()) {
                                buildMonitor.subTask("Building " + project.getName() + "...");
                                project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, buildMonitor.split(1));
                            }
                        }
                        jobSteps.add("Built all open projects");
                    }

                    return Status.OK_STATUS;
                } catch (Exception e) {
                    jobError[0] = e;
                    return new Status(IStatus.ERROR, "eclipse.mcp.server", "Failed: " + e.getMessage(), e);
                }
            }
        };

        refreshAndBuild.setUser(true);
        refreshAndBuild.schedule();
        refreshAndBuild.join();

        if (jobError[0] != null) {
            throw jobError[0];
        }

        steps.addAll(jobSteps);
        return steps;
    }

    private List<ProblemInfo> checkCompilationErrors(List<String> projectNames) throws Exception {
        List<ProblemInfo> errors = new ArrayList<>();

        if (projectNames != null && !projectNames.isEmpty()) {
            for (String name : projectNames) {
                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
                if (!project.exists()) continue;
                collectErrors(project, GetProblemsTool.JAVA_PROBLEM_MARKER, errors);
            }
        } else {
            collectErrors(ResourcesPlugin.getWorkspace().getRoot(), GetProblemsTool.JAVA_PROBLEM_MARKER, errors);
        }
        return errors;
    }

    private void collectErrors(IResource scope, String markerType, List<ProblemInfo> errors) throws Exception {
        IMarker[] markers = scope.findMarkers(markerType, true, IResource.DEPTH_INFINITE);
        for (IMarker marker : markers) {
            if (marker.getAttribute(IMarker.SEVERITY, -1) == IMarker.SEVERITY_ERROR) {
                int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
                errors.add(ProblemInfo.builder()
                        .project(marker.getResource().getProject().getName())
                        .file(marker.getResource().getProjectRelativePath().toString())
                        .line(line >= 0 ? line : null)
                        .message(marker.getAttribute(IMarker.MESSAGE, ""))
                        .build());
            }
        }
    }
}
