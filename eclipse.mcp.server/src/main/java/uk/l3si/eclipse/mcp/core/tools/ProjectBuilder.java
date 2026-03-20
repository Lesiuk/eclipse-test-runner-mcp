package uk.l3si.eclipse.mcp.core.tools;

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
import java.util.Arrays;
import java.util.List;

/**
 * Shared refresh + incremental build logic used by tools that need
 * up-to-date compilation state (run_test, get_problems, etc.).
 */
final class ProjectBuilder {

    private ProjectBuilder() {}

    /**
     * Build an error message for a missing project, listing available open projects.
     */
    static String projectNotFoundMessage(String name) {
        List<String> open = Arrays.stream(ResourcesPlugin.getWorkspace().getRoot().getProjects())
                .filter(IProject::isOpen)
                .map(IProject::getName)
                .toList();
        return "Project not found: " + name + ". Available projects: " + open;
    }

    /**
     * Clean and fully rebuild the given projects.  Use when incremental
     * build state is suspected to be stale or corrupt.
     *
     * @param projectNames specific projects to clean/rebuild, or {@code null} for all open projects
     * @return names of projects that were cleaned and rebuilt
     */
    static List<String> cleanAndBuild(List<String> projectNames) throws Exception {
        final Exception[] jobError = { null };
        final List<String> builtProjects = new ArrayList<>();

        Job job = new Job("MCP: Clean & Rebuild") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    IProject[] projects = resolveProjects(projectNames);
                    SubMonitor sub = SubMonitor.convert(monitor, projects.length * 3);
                    doCleanAndBuild(projects, builtProjects, sub);
                    return Status.OK_STATUS;
                } catch (Exception e) {
                    jobError[0] = e;
                    return Status.OK_STATUS;
                }
            }
        };

        job.setSystem(true);
        job.schedule();
        job.join();

        if (jobError[0] != null) {
            throw jobError[0];
        }

        return builtProjects;
    }

    /**
     * Resolve project names to IProject handles.  If {@code projectNames} is
     * null or empty, returns all projects in the workspace.
     */
    static IProject[] resolveProjects(List<String> projectNames) {
        if (projectNames != null && !projectNames.isEmpty()) {
            IProject[] projects = new IProject[projectNames.size()];
            for (int i = 0; i < projectNames.size(); i++) {
                String name = projectNames.get(i);
                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
                if (!project.exists()) {
                    throw new IllegalArgumentException(projectNotFoundMessage(name));
                }
                projects[i] = project;
            }
            return projects;
        }
        return ResourcesPlugin.getWorkspace().getRoot().getProjects();
    }

    /**
     * Performs refresh, clean, and full-build on the given projects.
     * Extracted for testability — call from a Job for async execution.
     */
    static void doCleanAndBuild(IProject[] projects, List<String> builtProjects,
                                IProgressMonitor monitor) throws Exception {
        // Refresh all
        for (IProject project : projects) {
            if (project.isOpen()) {
                project.refreshLocal(IResource.DEPTH_INFINITE, monitor);
            }
        }

        // Clean all
        for (IProject project : projects) {
            if (project.isOpen()) {
                project.build(IncrementalProjectBuilder.CLEAN_BUILD, monitor);
            }
        }

        // Wait for any auto-build triggered by clean
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);

        // Full build all
        for (IProject project : projects) {
            if (project.isOpen()) {
                project.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
                builtProjects.add(project.getName());
            }
        }

        // Wait for auto-build to finish
        Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
    }

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
                                throw new IllegalArgumentException(projectNotFoundMessage(name));
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
                    // Return OK to avoid Eclipse error dialog - error is thrown to caller after job completes
                    return Status.OK_STATUS;
                }
            }
        };

        job.setSystem(true);
        job.schedule();
        job.join();

        if (jobError[0] != null) {
            throw jobError[0];
        }

        return builtProjects;
    }
}
