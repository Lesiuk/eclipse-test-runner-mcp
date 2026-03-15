package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.ProblemInfo;
import uk.l3si.eclipse.mcp.model.ProblemsResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.McpTool;
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

public class GetProblemsTool implements McpTool {

    static final String JAVA_PROBLEM_MARKER = "org.eclipse.jdt.core.problem";

    @Override
    public String getName() {
        return "get_problems";
    }

    @Override
    public String getDescription() {
        return "Get compilation errors from Eclipse. Returns problems from the Problems view (Java compiler errors, missing imports, syntax errors, etc.). By default returns only errors. Optionally filter by project name.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("project", PropertySchema.string("Optional: project name to filter problems. If omitted, returns problems from all projects."))
                .property("includeWarnings", PropertySchema.bool("Include warnings in addition to errors. Default: false"))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String projectName = args.getString("project");
        boolean includeWarnings = args.getBoolean("includeWarnings");

        IResource scope;
        if (projectName != null) {
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            if (!project.exists()) {
                throw new IllegalArgumentException("Project not found: " + projectName);
            }
            scope = project;
        } else {
            scope = ResourcesPlugin.getWorkspace().getRoot();
        }

        refreshAndBuild(scope);

        // Only Java compilation problems — filters out jBPM, XML, and other marker types
        IMarker[] markers = scope.findMarkers(JAVA_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);

        int errorCount = 0;
        int warningCount = 0;
        List<ProblemInfo> errors = new ArrayList<>();
        List<ProblemInfo> warnings = new ArrayList<>();

        for (IMarker marker : markers) {
            int severity = marker.getAttribute(IMarker.SEVERITY, -1);
            if (severity == IMarker.SEVERITY_ERROR) {
                // always include errors
            } else if (severity == IMarker.SEVERITY_WARNING && includeWarnings) {
                // include warnings only if requested
            } else {
                continue;
            }

            int line = marker.getAttribute(IMarker.LINE_NUMBER, -1);
            ProblemInfo problem = ProblemInfo.builder()
                    .project(marker.getResource().getProject().getName())
                    .file(marker.getResource().getProjectRelativePath().toString())
                    .line(line >= 0 ? line : null)
                    .message(marker.getAttribute(IMarker.MESSAGE, ""))
                    .build();

            if (severity == IMarker.SEVERITY_ERROR) {
                errorCount++;
                errors.add(problem);
            } else {
                warningCount++;
                warnings.add(problem);
            }
        }

        return ProblemsResult.builder()
                .errorCount(errorCount)
                .warningCount(warningCount)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    private void refreshAndBuild(IResource scope) throws Exception {
        final Exception[] jobError = { null };

        Job refreshAndBuild = new Job("MCP: Refresh & Build for Problems") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    SubMonitor sub = SubMonitor.convert(monitor, 100);

                    if (scope instanceof IProject) {
                        IProject project = (IProject) scope;
                        sub.subTask("Refreshing " + project.getName() + "...");
                        project.refreshLocal(IResource.DEPTH_INFINITE, sub.split(30));

                        sub.subTask("Waiting for auto-build...");
                        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, sub.split(10));

                        sub.subTask("Building " + project.getName() + "...");
                        project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, sub.split(60));
                    } else {
                        IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

                        SubMonitor refreshMonitor = sub.split(30);
                        refreshMonitor.setWorkRemaining(allProjects.length);
                        for (IProject project : allProjects) {
                            if (project.isOpen()) {
                                refreshMonitor.subTask("Refreshing " + project.getName() + "...");
                                project.refreshLocal(IResource.DEPTH_INFINITE, refreshMonitor.split(1));
                            }
                        }

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
                    }

                    return Status.OK_STATUS;
                } catch (Exception e) {
                    jobError[0] = e;
                    return new Status(IStatus.ERROR, "eclipse.mcp.server", "Refresh & build failed: " + e.getMessage(), e);
                }
            }
        };

        refreshAndBuild.setUser(true);
        refreshAndBuild.schedule();
        refreshAndBuild.join();

        if (jobError[0] != null) {
            throw jobError[0];
        }
    }
}
