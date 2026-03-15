package uk.l3si.eclipse.mcp.tools.impl;

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

/**
 * Shared refresh + incremental build logic used by tools that need
 * up-to-date compilation state (run_test, get_problems, etc.).
 */
final class ProjectBuilder {

    private ProjectBuilder() {}

    /**
     * Refresh and incrementally build the given projects.
     *
     * @param projectNames specific projects to build, or {@code null} to build all open projects
     * @return names of projects that were actually built
     */
    static List<String> refreshAndBuild(List<String> projectNames) throws Exception {
        final Exception[] jobError = { null };
        final List<String> builtProjects = new ArrayList<>();

        Job job = new Job("MCP: Refresh & Build") {
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

                            // Wait for auto-build after refresh
                            sub.subTask("Waiting for auto-build...");
                            Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);

                            // Build
                            buildMonitor.subTask("Building " + name + "...");
                            project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, buildMonitor.split(1));

                            builtProjects.add(name);
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

                        sub.subTask("Waiting for auto-build...");
                        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, sub.split(10));

                        SubMonitor buildMonitor = sub.split(60);
                        buildMonitor.setWorkRemaining(allProjects.length);
                        for (IProject project : allProjects) {
                            if (project.isOpen()) {
                                buildMonitor.subTask("Building " + project.getName() + "...");
                                project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, buildMonitor.split(1));
                                builtProjects.add(project.getName());
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

        job.setUser(true);
        job.schedule();
        job.join();

        if (jobError[0] != null) {
            throw jobError[0];
        }

        return builtProjects;
    }
}
