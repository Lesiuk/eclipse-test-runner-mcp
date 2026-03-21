package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CleanBuildToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(CleanBuildTool tool, JsonObject args) throws Exception {
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

    private IWorkspaceRoot mockWorkspaceRoot(IMarker... markers) throws Exception {
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        when(root.findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE)))
                .thenReturn(markers);
        return root;
    }

    private void setupWorkspaceMock(MockedStatic<ResourcesPlugin> rsMock, IWorkspaceRoot root) {
        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);
        rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
    }

    // --- Tool metadata tests ---

    @Test
    void toolNameIsCleanBuild() {
        assertEquals("clean_build", new CleanBuildTool().getName());
    }

    @Test
    void descriptionMentionsCleanAndRebuild() {
        String desc = new CleanBuildTool().getDescription();
        assertTrue(desc.toLowerCase().contains("clean"), "description should mention clean");
        assertTrue(desc.toLowerCase().contains("rebuild"), "description should mention rebuild");
    }

    @Test
    void inputSchemaHasProjectsProperty() {
        InputSchema schema = new CleanBuildTool().getInputSchema();
        assertTrue(schema.getPropertyNames().contains("projects"),
                "schema should have 'projects' property");
    }

    @Test
    void inputSchemaOnlyHasProjectsProperty() {
        InputSchema schema = new CleanBuildTool().getInputSchema();
        assertEquals(1, schema.getPropertyNames().size(),
                "schema should have exactly one property");
    }

    // --- Project forwarding tests ---

    @Test
    void executeWithSpecificProjectsForwardsThem() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(eq(List.of("projA", "projB")), any()))
                    .thenReturn(List.of("projA", "projB"));
            setupWorkspaceMock(rsMock, root);

            JsonObject args = new JsonObject();
            JsonArray projects = new JsonArray();
            projects.add("projA");
            projects.add("projB");
            args.add("projects", projects);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), args);

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(2, builtProjects.size());
            assertEquals("projA", builtProjects.get(0).getAsString());
            assertEquals("projB", builtProjects.get(1).getAsString());

            pbMock.verify(() -> ProjectBuilder.cleanAndBuild(eq(List.of("projA", "projB")), any()));
        }
    }

    @Test
    void executeWithNoArgsPassesNull() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(isNull(), any()))
                    .thenReturn(List.of("all-project"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(1, builtProjects.size());
            assertEquals("all-project", builtProjects.get(0).getAsString());

            pbMock.verify(() -> ProjectBuilder.cleanAndBuild(isNull(), any()));
        }
    }

    @Test
    void executeWithEmptyProjectsArrayPassesEmptyList() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(eq(List.of()), any()))
                    .thenReturn(List.of("workspace-project"));
            setupWorkspaceMock(rsMock, root);

            JsonObject args = new JsonObject();
            args.add("projects", new JsonArray());

            JsonObject result = executeAndSerialize(new CleanBuildTool(), args);

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(1, builtProjects.size());
            assertEquals("workspace-project", builtProjects.get(0).getAsString());
        }
    }

    @Test
    void executePropagatesException() {
        try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
            mocked.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenThrow(new IllegalArgumentException("Project not found: ghost"));

            JsonObject args = new JsonObject();
            JsonArray projects = new JsonArray();
            projects.add("ghost");
            args.add("projects", projects);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new CleanBuildTool().execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("ghost"));
        }
    }

    @Test
    void resultContainsAllBuiltProjects() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("alpha", "beta", "gamma"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(3, builtProjects.size());
            assertEquals("alpha", builtProjects.get(0).getAsString());
            assertEquals("beta", builtProjects.get(1).getAsString());
            assertEquals("gamma", builtProjects.get(2).getAsString());
        }
    }

    @Test
    void executeReportsProgressViaReporter() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        var messages = new java.util.ArrayList<String>();
        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenAnswer(invocation -> {
                        ProgressReporter progress = invocation.getArgument(1);
                        progress.report("Refreshing projA...");
                        progress.report("Building projA...");
                        return List.of("projA");
                    });
            setupWorkspaceMock(rsMock, root);

            new CleanBuildTool().execute(new Args(new JsonObject()), messages::add);

            assertEquals(2, messages.size());
            assertTrue(messages.get(0).contains("Refreshing"));
            assertTrue(messages.get(1).contains("Building"));
        }
    }

    // --- Problem reporting tests ---

    @Test
    void noProblemsAfterBuild() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(0, problems.get("errorCount").getAsInt());
            assertEquals(0, problems.get("warningCount").getAsInt());
            assertEquals(0, problems.getAsJsonArray("errors").size());
            assertEquals(0, problems.getAsJsonArray("warnings").size());
        }
    }

    @Test
    void errorsReturnedAfterBuild() throws Exception {
        IMarker errorMarker = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/Main.java", 10, "cannot resolve symbol");
        IWorkspaceRoot root = mockWorkspaceRoot(errorMarker);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(1, problems.get("errorCount").getAsInt());
            assertEquals(0, problems.get("warningCount").getAsInt());
            assertTrue(problems.getAsJsonArray("errors").size() > 0);
            assertEquals(0, problems.getAsJsonArray("warnings").size());
        }
    }

    @Test
    void warningsReturnedAfterBuild() throws Exception {
        IMarker warningMarker = mockMarker(IMarker.SEVERITY_WARNING, "myProject", "src/Foo.java", 5, "unused import");
        IWorkspaceRoot root = mockWorkspaceRoot(warningMarker);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(0, problems.get("errorCount").getAsInt());
            assertEquals(1, problems.get("warningCount").getAsInt());
            assertEquals(0, problems.getAsJsonArray("errors").size());
            assertTrue(problems.getAsJsonArray("warnings").size() > 0);
        }
    }

    @Test
    void errorsAndWarningsReturnedTogether() throws Exception {
        IMarker errorMarker = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/Main.java", 10, "cannot resolve symbol");
        IMarker warningMarker = mockMarker(IMarker.SEVERITY_WARNING, "myProject", "src/Foo.java", 5, "unused import");
        IWorkspaceRoot root = mockWorkspaceRoot(errorMarker, warningMarker);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(1, problems.get("errorCount").getAsInt());
            assertEquals(1, problems.get("warningCount").getAsInt());
            assertTrue(problems.getAsJsonArray("errors").size() > 0);
            assertTrue(problems.getAsJsonArray("warnings").size() > 0);
        }
    }

    @Test
    void infoSeverityMarkersIgnored() throws Exception {
        IMarker infoMarker = mockMarker(IMarker.SEVERITY_INFO, "myProject", "src/Main.java", 1, "info message");
        IWorkspaceRoot root = mockWorkspaceRoot(infoMarker);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(0, problems.get("errorCount").getAsInt());
            assertEquals(0, problems.get("warningCount").getAsInt());
        }
    }

    @Test
    void errorsAreGroupedByProjectAndMessage() throws Exception {
        IMarker error1 = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/A.java", 10, "cannot resolve symbol");
        IMarker error2 = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/B.java", 20, "cannot resolve symbol");
        IMarker error3 = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/C.java", 30, "different error");
        IWorkspaceRoot root = mockWorkspaceRoot(error1, error2, error3);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(3, problems.get("errorCount").getAsInt());

            JsonArray errors = problems.getAsJsonArray("errors");
            assertEquals(2, errors.size());

            // Most frequent first
            JsonObject firstGroup = errors.get(0).getAsJsonObject();
            assertEquals("cannot resolve symbol", firstGroup.get("message").getAsString());
            assertEquals(2, firstGroup.get("count").getAsInt());

            JsonObject secondGroup = errors.get(1).getAsJsonObject();
            assertEquals("different error", secondGroup.get("message").getAsString());
            assertEquals(1, secondGroup.get("count").getAsInt());
        }
    }

    @Test
    void problemLineNegativeOneBecomesNull() throws Exception {
        IMarker errorMarker = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/Main.java", -1, "parse error");
        IWorkspaceRoot root = mockWorkspaceRoot(errorMarker);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            JsonArray errors = problems.getAsJsonArray("errors");
            JsonObject group = errors.get(0).getAsJsonObject();
            JsonArray locations = group.getAsJsonArray("locations");
            JsonObject loc = locations.get(0).getAsJsonObject();
            assertFalse(loc.has("line"),
                    "line should be absent when marker has no line number");
        }
    }

    @Test
    void multipleProjectErrorsReported() throws Exception {
        IMarker error1 = mockMarker(IMarker.SEVERITY_ERROR, "projA", "src/A.java", 1, "error in A");
        IMarker error2 = mockMarker(IMarker.SEVERITY_ERROR, "projB", "src/B.java", 2, "error in B");
        IWorkspaceRoot root = mockWorkspaceRoot(error1, error2);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("projA", "projB"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(2, problems.get("errorCount").getAsInt());
            assertEquals(2, problems.getAsJsonArray("errors").size());
        }
    }

    @Test
    void markersQueriedFromWorkspaceRoot() throws Exception {
        IWorkspaceRoot root = mockWorkspaceRoot();

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            executeAndSerialize(new CleanBuildTool(), new JsonObject());

            verify(root).findMarkers(eq(GetProblemsTool.JAVA_PROBLEM_MARKER), eq(true), eq(IResource.DEPTH_INFINITE));
        }
    }

    @Test
    void projectsAndProblemsReturnedTogether() throws Exception {
        IMarker errorMarker = mockMarker(IMarker.SEVERITY_ERROR, "myProject", "src/Main.java", 10, "cannot resolve symbol");
        IWorkspaceRoot root = mockWorkspaceRoot(errorMarker);

        try (MockedStatic<ProjectBuilder> pbMock = mockStatic(ProjectBuilder.class);
             MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class)) {

            pbMock.when(() -> ProjectBuilder.cleanAndBuild(any(), any()))
                    .thenReturn(List.of("myProject"));
            setupWorkspaceMock(rsMock, root);

            JsonObject result = executeAndSerialize(new CleanBuildTool(), new JsonObject());

            // Projects at top level
            assertEquals(1, result.getAsJsonArray("projects").size());
            assertEquals("myProject", result.getAsJsonArray("projects").get(0).getAsString());

            // Problems nested
            JsonObject problems = result.getAsJsonObject("problems");
            assertEquals(1, problems.get("errorCount").getAsInt());
            assertTrue(problems.getAsJsonArray("errors").size() > 0);
        }
    }
}
