package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GetProblemsToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(GetProblemsTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    private IMarker mockMarker(int severity, String projectName, String filePath, int line, String message) {
        IMarker marker = mock(IMarker.class);
        when(marker.getAttribute(IMarker.SEVERITY, -1)).thenReturn(severity);
        when(marker.getAttribute(IMarker.LINE_NUMBER, -1)).thenReturn(line);
        when(marker.getAttribute(IMarker.MESSAGE, "")).thenReturn(message);

        IResource resource = mock(IResource.class);
        IProject project = mock(IProject.class);
        when(project.getName()).thenReturn(projectName);
        when(resource.getProject()).thenReturn(project);

        IPath path = mock(IPath.class);
        when(path.toString()).thenReturn(filePath);
        when(resource.getProjectRelativePath()).thenReturn(path);

        when(marker.getResource()).thenReturn(resource);
        return marker;
    }

    private IWorkspaceRoot mockWorkspaceRoot() {
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);
        return root;
    }

    @Test
    void nameIsGetProblems() {
        GetProblemsTool tool = new GetProblemsTool();
        assertEquals("get_problems", tool.getName());
    }

    @Test
    void noProblems() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();
        when(root.findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE)))
                .thenReturn(new IMarker[]{});

        try (MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class)) {

            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            GetProblemsTool tool = new GetProblemsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            assertEquals(0, result.get("errorCount").getAsInt());
            assertEquals(0, result.get("warningCount").getAsInt());
            assertEquals(0, result.getAsJsonArray("errors").size());
            assertEquals(0, result.getAsJsonArray("warnings").size());
        }
    }

    @Test
    void errorsOnly() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();
        IMarker errorMarker = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/Main.java", 10, "cannot resolve symbol");

        when(root.findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE)))
                .thenReturn(new IMarker[]{errorMarker});

        try (MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class)) {

            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            GetProblemsTool tool = new GetProblemsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            assertEquals(1, result.get("errorCount").getAsInt());
            assertEquals(0, result.get("warningCount").getAsInt());
            assertTrue(result.getAsJsonArray("errors").size() > 0);
        }
    }

    @Test
    void warningsExcludedByDefault() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();
        IMarker warningMarker = mockMarker(IMarker.SEVERITY_WARNING, "myProject", "src/Foo.java", 5, "unused import");

        when(root.findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE)))
                .thenReturn(new IMarker[]{warningMarker});

        try (MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class)) {

            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            GetProblemsTool tool = new GetProblemsTool();
            JsonObject args = new JsonObject();
            args.addProperty("includeWarnings", false);
            JsonObject result = executeAndSerialize(tool, args);

            assertEquals(0, result.get("errorCount").getAsInt());
            assertEquals(0, result.get("warningCount").getAsInt());
        }
    }

    @Test
    void warningsIncluded() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();
        IMarker errorMarker = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/Main.java", 10, "cannot resolve symbol");
        IMarker warningMarker = mockMarker(IMarker.SEVERITY_WARNING, "myProject", "src/Foo.java", 5, "unused import");

        when(root.findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE)))
                .thenReturn(new IMarker[]{errorMarker, warningMarker});

        try (MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class)) {

            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            GetProblemsTool tool = new GetProblemsTool();
            JsonObject args = new JsonObject();
            args.addProperty("includeWarnings", true);
            JsonObject result = executeAndSerialize(tool, args);

            assertEquals(1, result.get("errorCount").getAsInt());
            assertEquals(1, result.get("warningCount").getAsInt());
            assertTrue(result.getAsJsonArray("errors").size() > 0);
            assertTrue(result.getAsJsonArray("warnings").size() > 0);
        }
    }

    @Test
    void projectNotFound() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();
        IProject project = mock(IProject.class);
        when(project.exists()).thenReturn(false);
        when(root.getProject("ghost")).thenReturn(project);

        try (MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class)) {

            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            pbMock.when(() -> ProjectBuilder.projectNotFoundMessage("ghost"))
                    .thenReturn("Project not found: ghost. Available projects: []");

            GetProblemsTool tool = new GetProblemsTool();
            JsonObject args = new JsonObject();
            args.addProperty("project", "ghost");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("ghost"));
        }
    }

    @Test
    void filterByProject() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();
        IProject project = mock(IProject.class);
        when(project.exists()).thenReturn(true);
        when(root.getProject("myProject")).thenReturn(project);
        when(project.findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE)))
                .thenReturn(new IMarker[]{});

        try (MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class)) {

            IWorkspace workspace = mock(IWorkspace.class);
            when(workspace.getRoot()).thenReturn(root);
            rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            GetProblemsTool tool = new GetProblemsTool();
            JsonObject args = new JsonObject();
            args.addProperty("project", "myProject");
            JsonObject result = executeAndSerialize(tool, args);

            assertEquals(0, result.get("errorCount").getAsInt());
            assertEquals(0, result.get("warningCount").getAsInt());

            // Verify findMarkers was called on the project, not the root
            verify(project).findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE));
            verify(root, never()).findMarkers(any(), anyBoolean(), anyInt());
        }
    }
}
