package uk.l3si.eclipse.mcp.core.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import uk.l3si.eclipse.mcp.tools.Args;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FindReferencesToolTest {

    private static final Gson GSON = new Gson();

    // --- helpers ---

    private MockedStatic<ResourcesPlugin> mockWorkspace(IProject... projects) {
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        when(root.getProjects()).thenReturn(projects);
        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);

        MockedStatic<ResourcesPlugin> rsMock = mockStatic(ResourcesPlugin.class);
        rsMock.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
        return rsMock;
    }

    // --- name ---

    @Test
    void nameIsFindReferences() {
        assertEquals("find_references", new FindReferencesTool().getName());
    }

    // --- missing args ---

    @Test
    void missingClassThrows() {
        FindReferencesTool tool = new FindReferencesTool();
        JsonObject args = new JsonObject();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(new Args(args), message -> {}));
        assertTrue(ex.getMessage().contains("class"));
    }

    // --- class not found: empty workspace ---

    @Test
    void classNotFoundEmptyWorkspace() {
        try (MockedStatic<ResourcesPlugin> rs = mockWorkspace()) {
            FindReferencesTool tool = new FindReferencesTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.Missing");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("com.example.Missing"));
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    // --- class not found: no Java projects ---

    @Test
    void classNotFoundNoJavaProjects() throws Exception {
        IProject project = mock(IProject.class);
        when(project.isOpen()).thenReturn(true);
        when(project.hasNature(JavaCore.NATURE_ID)).thenReturn(false);

        try (MockedStatic<ResourcesPlugin> rs = mockWorkspace(project)) {
            FindReferencesTool tool = new FindReferencesTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.Missing");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    // --- class not found: all projects closed ---

    @Test
    void classNotFoundClosedProjects() throws Exception {
        IProject project = mock(IProject.class);
        when(project.isOpen()).thenReturn(false);

        try (MockedStatic<ResourcesPlugin> rs = mockWorkspace(project)) {
            FindReferencesTool tool = new FindReferencesTool();
            JsonObject args = new JsonObject();
            args.addProperty("class", "com.example.Missing");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args), message -> {}));
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    // --- input schema ---

    @Test
    void inputSchemaIsNotNull() {
        FindReferencesTool tool = new FindReferencesTool();
        assertNotNull(tool.getInputSchema());
    }
}
