package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProjectBuilderTest {

    private IWorkspaceRoot setupWorkspaceRoot() {
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);
        return root;
    }

    private IProject mockProject(String name, boolean exists, boolean open) {
        IProject project = mock(IProject.class);
        when(project.getName()).thenReturn(name);
        when(project.exists()).thenReturn(exists);
        when(project.isOpen()).thenReturn(open);
        return project;
    }

    // ── resolveProjects ──────────────────────────────────────────

    @Test
    void resolveProjectsWithNamedProjectsReturnsMatching() {
        IWorkspaceRoot root = setupWorkspaceRoot();
        IProject projA = mockProject("projA", true, true);
        IProject projB = mockProject("projB", true, true);
        when(root.getProject("projA")).thenReturn(projA);
        when(root.getProject("projB")).thenReturn(projB);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            IProject[] result = ProjectBuilder.resolveProjects(List.of("projA", "projB"));
            assertEquals(2, result.length);
            assertSame(projA, result[0]);
            assertSame(projB, result[1]);
        }
    }

    @Test
    void resolveProjectsThrowsForNonexistentProject() {
        IWorkspaceRoot root = setupWorkspaceRoot();
        IProject ghost = mockProject("ghost", false, false);
        when(root.getProject("ghost")).thenReturn(ghost);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> ProjectBuilder.resolveProjects(List.of("ghost")));
            assertTrue(ex.getMessage().contains("ghost"));
        }
    }

    @Test
    void resolveProjectsWithNullReturnsAllProjects() {
        IWorkspaceRoot root = setupWorkspaceRoot();
        IProject[] allProjects = { mockProject("all1", true, true), mockProject("all2", true, true) };
        when(root.getProjects()).thenReturn(allProjects);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            IProject[] result = ProjectBuilder.resolveProjects(null);
            assertSame(allProjects, result);
        }
    }

    @Test
    void resolveProjectsWithEmptyListReturnsAllProjects() {
        IWorkspaceRoot root = setupWorkspaceRoot();
        IProject[] allProjects = { mockProject("x", true, true) };
        when(root.getProjects()).thenReturn(allProjects);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            IProject[] result = ProjectBuilder.resolveProjects(List.of());
            assertSame(allProjects, result);
        }
    }

    // ── doCleanAndBuild ──────────────────────────────────────────

    @Test
    void doCleanAndBuildRefreshesThenCleansThenBuilds() throws Exception {
        IProject project = mockProject("myProject", true, true);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{project}, builtProjects, monitor, message -> {});

            // Verify order: refresh → clean → full build
            InOrder inOrder = inOrder(project);
            inOrder.verify(project).refreshLocal(eq(IResource.DEPTH_INFINITE), any());
            inOrder.verify(project).build(eq(IncrementalProjectBuilder.CLEAN_BUILD), any());
            inOrder.verify(project).build(eq(IncrementalProjectBuilder.FULL_BUILD), any());

            assertEquals(List.of("myProject"), builtProjects);
        }
    }

    @Test
    void doCleanAndBuildProcessesMultipleProjects() throws Exception {
        IProject projA = mockProject("A", true, true);
        IProject projB = mockProject("B", true, true);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{projA, projB}, builtProjects, monitor, message -> {});

            verify(projA).refreshLocal(eq(IResource.DEPTH_INFINITE), any());
            verify(projB).refreshLocal(eq(IResource.DEPTH_INFINITE), any());
            verify(projA).build(eq(IncrementalProjectBuilder.CLEAN_BUILD), any());
            verify(projB).build(eq(IncrementalProjectBuilder.CLEAN_BUILD), any());
            verify(projA).build(eq(IncrementalProjectBuilder.FULL_BUILD), any());
            verify(projB).build(eq(IncrementalProjectBuilder.FULL_BUILD), any());

            assertEquals(List.of("A", "B"), builtProjects);
        }
    }

    @Test
    void doCleanAndBuildSkipsClosedProjects() throws Exception {
        IProject open = mockProject("open", true, true);
        IProject closed = mockProject("closed", true, false);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{open, closed}, builtProjects, monitor, message -> {});

            verify(open).refreshLocal(eq(IResource.DEPTH_INFINITE), any());
            verify(closed, never()).refreshLocal(anyInt(), any());
            verify(open).build(eq(IncrementalProjectBuilder.CLEAN_BUILD), any());
            verify(closed, never()).build(anyInt(), any());
            verify(open).build(eq(IncrementalProjectBuilder.FULL_BUILD), any());

            assertEquals(List.of("open"), builtProjects);
        }
    }

    @Test
    void doCleanAndBuildWaitsForAutoBuildTwice() throws Exception {
        IProject project = mockProject("p", true, true);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{project}, builtProjects, monitor, message -> {});

            // Should join auto-build family twice: once after clean, once after full build
            verify(jobManager, times(2)).join(eq(ResourcesPlugin.FAMILY_AUTO_BUILD), any());
        }
    }

    @Test
    void doCleanAndBuildWithEmptyArrayReturnsEmpty() throws Exception {
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{}, builtProjects, monitor, message -> {});

            assertTrue(builtProjects.isEmpty());
        }
    }

    @Test
    void doCleanAndBuildUsesCleanBuildNotIncremental() throws Exception {
        IProject project = mockProject("proj", true, true);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{project}, builtProjects, monitor, message -> {});

            // Must NOT use incremental build
            verify(project, never()).build(eq(IncrementalProjectBuilder.INCREMENTAL_BUILD), any());
            // Must use clean + full
            verify(project).build(eq(IncrementalProjectBuilder.CLEAN_BUILD), any());
            verify(project).build(eq(IncrementalProjectBuilder.FULL_BUILD), any());
        }
    }

    @Test
    void doCleanAndBuildRefreshesBeforeClean() throws Exception {
        IProject project = mockProject("proj", true, true);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{project}, builtProjects, monitor, message -> {});

            // Verify refresh happens before any build call
            InOrder inOrder = inOrder(project);
            inOrder.verify(project).refreshLocal(eq(IResource.DEPTH_INFINITE), any());
            inOrder.verify(project).build(anyInt(), any()); // clean
            inOrder.verify(project).build(anyInt(), any()); // full
        }
    }

    @Test
    void doCleanAndBuildReportsProgress() throws Exception {
        IProject projA = mockProject("A", true, true);
        IProject projB = mockProject("B", true, true);
        List<String> builtProjects = new ArrayList<>();
        IProgressMonitor monitor = new NullProgressMonitor();
        var messages = new ArrayList<String>();

        try (MockedStatic<Job> jobMock = mockStatic(Job.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {
            IJobManager jobManager = mock(IJobManager.class);
            jobMock.when(Job::getJobManager).thenReturn(jobManager);

            ProjectBuilder.doCleanAndBuild(new IProject[]{projA, projB}, builtProjects, monitor, messages::add);

            assertTrue(messages.stream().anyMatch(m -> m.contains("Refreshing") && m.contains("A")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("Cleaning") && m.contains("A")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("Building") && m.contains("A")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("Refreshing") && m.contains("B")));
            assertTrue(messages.stream().anyMatch(m -> m.contains("Building") && m.contains("B")));
        }
    }
}
