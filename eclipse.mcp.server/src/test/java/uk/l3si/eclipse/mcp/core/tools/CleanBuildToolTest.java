package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CleanBuildToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(CleanBuildTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void toolNameIsCleanBuild() {
        CleanBuildTool tool = new CleanBuildTool();
        assertEquals("clean_build", tool.getName());
    }

    @Test
    void descriptionMentionsCleanAndRebuild() {
        CleanBuildTool tool = new CleanBuildTool();
        String desc = tool.getDescription();
        assertTrue(desc.toLowerCase().contains("clean"), "description should mention clean");
        assertTrue(desc.toLowerCase().contains("rebuild"), "description should mention rebuild");
    }

    @Test
    void inputSchemaHasProjectsProperty() {
        CleanBuildTool tool = new CleanBuildTool();
        InputSchema schema = tool.getInputSchema();
        assertTrue(schema.getPropertyNames().contains("projects"),
                "schema should have 'projects' property");
    }

    @Test
    void inputSchemaOnlyHasProjectsProperty() {
        CleanBuildTool tool = new CleanBuildTool();
        InputSchema schema = tool.getInputSchema();
        assertEquals(1, schema.getPropertyNames().size(),
                "schema should have exactly one property");
    }

    @Test
    void executeWithSpecificProjectsForwardsThem() throws Exception {
        try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
            mocked.when(() -> ProjectBuilder.cleanAndBuild(List.of("projA", "projB")))
                    .thenReturn(List.of("projA", "projB"));

            CleanBuildTool tool = new CleanBuildTool();
            JsonObject args = new JsonObject();
            JsonArray projects = new JsonArray();
            projects.add("projA");
            projects.add("projB");
            args.add("projects", projects);

            JsonObject result = executeAndSerialize(tool, args);

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(2, builtProjects.size());
            assertEquals("projA", builtProjects.get(0).getAsString());
            assertEquals("projB", builtProjects.get(1).getAsString());

            mocked.verify(() -> ProjectBuilder.cleanAndBuild(List.of("projA", "projB")));
        }
    }

    @Test
    void executeWithNoArgsPassesNull() throws Exception {
        try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
            mocked.when(() -> ProjectBuilder.cleanAndBuild(null))
                    .thenReturn(List.of("all-project"));

            CleanBuildTool tool = new CleanBuildTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(1, builtProjects.size());
            assertEquals("all-project", builtProjects.get(0).getAsString());

            mocked.verify(() -> ProjectBuilder.cleanAndBuild(null));
        }
    }

    @Test
    void executeWithEmptyProjectsArrayPassesEmptyList() throws Exception {
        try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
            mocked.when(() -> ProjectBuilder.cleanAndBuild(List.of()))
                    .thenReturn(List.of("workspace-project"));

            CleanBuildTool tool = new CleanBuildTool();
            JsonObject args = new JsonObject();
            args.add("projects", new JsonArray());

            JsonObject result = executeAndSerialize(tool, args);

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(1, builtProjects.size());
            assertEquals("workspace-project", builtProjects.get(0).getAsString());
        }
    }

    @Test
    void executePropagatesException() throws Exception {
        try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
            mocked.when(() -> ProjectBuilder.cleanAndBuild(any()))
                    .thenThrow(new IllegalArgumentException("Project not found: ghost"));

            CleanBuildTool tool = new CleanBuildTool();
            JsonObject args = new JsonObject();
            JsonArray projects = new JsonArray();
            projects.add("ghost");
            args.add("projects", projects);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> tool.execute(new Args(args)));
            assertTrue(ex.getMessage().contains("ghost"));
        }
    }

    @Test
    void resultContainsAllBuiltProjects() throws Exception {
        try (MockedStatic<ProjectBuilder> mocked = mockStatic(ProjectBuilder.class)) {
            mocked.when(() -> ProjectBuilder.cleanAndBuild(any()))
                    .thenReturn(List.of("alpha", "beta", "gamma"));

            CleanBuildTool tool = new CleanBuildTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray builtProjects = result.getAsJsonArray("projects");
            assertEquals(3, builtProjects.size());
            assertEquals("alpha", builtProjects.get(0).getAsString());
            assertEquals("beta", builtProjects.get(1).getAsString());
            assertEquals("gamma", builtProjects.get(2).getAsString());
        }
    }
}
