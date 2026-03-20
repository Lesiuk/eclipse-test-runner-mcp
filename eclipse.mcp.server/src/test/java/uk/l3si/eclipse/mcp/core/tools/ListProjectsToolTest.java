package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ListProjectsToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(ListProjectsTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    private IProject mockProject(String name, boolean open) {
        IProject project = mock(IProject.class);
        when(project.getName()).thenReturn(name);
        when(project.isOpen()).thenReturn(open);
        return project;
    }

    @Test
    void nameIsListProjects() {
        ListProjectsTool tool = new ListProjectsTool();
        assertEquals("list_projects", tool.getName());
    }

    @Test
    void emptyWorkspace() throws Exception {
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        when(root.getProjects()).thenReturn(new IProject[]{});

        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            ListProjectsTool tool = new ListProjectsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray projects = result.getAsJsonArray("projects");
            assertEquals(0, projects.size());
        }
    }

    @Test
    void onlyOpenProjects() throws Exception {
        IProject openProject = mockProject("OpenProject", true);
        IProject closedProject = mockProject("ClosedProject", false);

        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        when(root.getProjects()).thenReturn(new IProject[]{openProject, closedProject});

        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            ListProjectsTool tool = new ListProjectsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray projects = result.getAsJsonArray("projects");
            assertEquals(1, projects.size());
            assertEquals("OpenProject", projects.get(0).getAsString());
        }
    }

    @Test
    void multipleOpenProjects() throws Exception {
        IProject alpha = mockProject("alpha", true);
        IProject beta = mockProject("beta", true);
        IProject gamma = mockProject("gamma", true);

        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        when(root.getProjects()).thenReturn(new IProject[]{alpha, beta, gamma});

        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);

        try (MockedStatic<ResourcesPlugin> mocked = mockStatic(ResourcesPlugin.class)) {
            mocked.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);

            ListProjectsTool tool = new ListProjectsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray projects = result.getAsJsonArray("projects");
            assertEquals(3, projects.size());
            assertEquals("alpha", projects.get(0).getAsString());
            assertEquals("beta", projects.get(1).getAsString());
            assertEquals("gamma", projects.get(2).getAsString());
        }
    }
}
