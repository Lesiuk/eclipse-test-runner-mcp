package uk.l3si.eclipse.mcp.core.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.debug.core.ILaunchConfiguration;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.model.LaunchTestResult;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunTestToolTest {

    private static RunTestTool createTool() {
        return new RunTestTool(Map.of("run", "Run tests"), null);
    }

    @Test
    void nameIsCorrect() {
        assertEquals("run_test", createTool().getName());
    }

    @Test
    void schemaIncludesMethodsParameter() {
        RunTestTool tool = createTool();
        assertTrue(tool.getInputSchema().getPropertyNames().contains("method"),
                "Schema should include 'method' property");
        assertTrue(tool.getInputSchema().getPropertyNames().contains("methods"),
                "Schema should include 'methods' property");
    }

    @Test
    void schemaIncludesPackageParameter() {
        assertTrue(createTool().getInputSchema().getPropertyNames().contains("package"),
                "Schema should include 'package' property");
    }

    @Test
    void packageTargetRejectsMethodSelectors() {
        JsonObject json = new JsonObject();
        json.addProperty("config", "Unit tests");
        json.addProperty("package", "com.example.unit");
        json.addProperty("method", "testOne");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createTool().execute(new Args(json), message -> { }));

        assertTrue(exception.getMessage().contains("Package"));
        assertTrue(exception.getMessage().contains("method"));
    }

    @Test
    void packageTargetRejectsMethodsArray() {
        JsonObject json = new JsonObject();
        json.addProperty("config", "Unit tests");
        json.addProperty("package", "com.example.unit");
        JsonArray methods = new JsonArray();
        methods.add("testOne");
        json.add("methods", methods);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> createTool().execute(new Args(json), message -> { }));

        assertTrue(exception.getMessage().contains("Package"));
        assertTrue(exception.getMessage().contains("methods"));
    }

    @Test
    void targetValidationRequiresExactlyOneTarget() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RunTestTool.validateTarget(null, null, null));

        assertTrue(exception.getMessage().contains("class"));
        assertTrue(exception.getMessage().contains("package"));
    }

    @Test
    void targetValidationRejectsClassAndPackageTogether() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> RunTestTool.validateTarget("com.example.FooTest", "com.example.unit", null));

        assertTrue(exception.getMessage().contains("mutually exclusive"));
    }

    @Test
    void packageTargetIsForwardedToLauncher() throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("config", "Unit tests");
        json.addProperty("package", "com.example.unit");
        json.addProperty("project", "unit-project");

        IProject project = mock(IProject.class);
        when(project.exists()).thenReturn(false);
        IWorkspaceRoot root = mock(IWorkspaceRoot.class);
        when(root.getProject("unit-project")).thenReturn(project);
        IWorkspace workspace = mock(IWorkspace.class);
        when(workspace.getRoot()).thenReturn(root);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        LaunchTestResult launchResult = LaunchTestResult.builder().build();

        try (MockedStatic<ResourcesPlugin> resources = mockStatic(ResourcesPlugin.class);
             MockedStatic<ProjectBuilder> projectBuilder = mockStatic(ProjectBuilder.class);
             MockedStatic<TestLaunchHelper> launcher = mockStatic(TestLaunchHelper.class)) {
            resources.when(ResourcesPlugin::getWorkspace).thenReturn(workspace);
            projectBuilder.when(() -> ProjectBuilder.refreshAndBuild(
                    eq(List.of("unit-project")), any(ProgressReporter.class)))
                    .thenReturn(List.of("unit-project"));
            launcher.when(() -> TestLaunchHelper.findTestConfig("Unit tests")).thenReturn(config);
            launcher.when(() -> TestLaunchHelper.launchTest(
                    eq("Unit tests"), isNull(), eq("com.example.unit"), isNull(),
                    eq("unit-project"), eq("run"), isNull(), any(ProgressReporter.class)))
                    .thenReturn(launchResult);

            List<String> progressMessages = new java.util.ArrayList<>();
            createTool().execute(new Args(json), progressMessages::add);

            launcher.verify(() -> TestLaunchHelper.launchTest(
                    eq("Unit tests"), isNull(), eq("com.example.unit"), isNull(),
                    eq("unit-project"), eq("run"), isNull(), any(ProgressReporter.class)));
            assertTrue(progressMessages.contains("Launching com.example.unit..."),
                    "package target should be visible in live progress");
        }
    }

    @Test
    void resolveMethods_methodOnly_returnsSingletonList() {
        JsonObject json = new JsonObject();
        json.addProperty("method", "testAdd");
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testAdd"), result);
    }

    @Test
    void resolveMethods_methodsOnly_returnsList() {
        JsonObject json = new JsonObject();
        JsonArray arr = new JsonArray();
        arr.add("testAdd");
        arr.add("testSubtract");
        json.add("methods", arr);
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testAdd", "testSubtract"), result);
    }

    @Test
    void resolveMethods_bothMergedAndDeduplicated() {
        JsonObject json = new JsonObject();
        json.addProperty("method", "testA");
        JsonArray arr = new JsonArray();
        arr.add("testB");
        arr.add("testA");
        json.add("methods", arr);
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testA", "testB"), result);
    }

    @Test
    void resolveMethods_neitherProvided_returnsNull() {
        JsonObject json = new JsonObject();
        assertNull(RunTestTool.resolveMethods(new Args(json)));
    }

    @Test
    void resolveMethods_emptyArrayNoMethod_returnsNull() {
        JsonObject json = new JsonObject();
        json.add("methods", new JsonArray());
        assertNull(RunTestTool.resolveMethods(new Args(json)));
    }

    @Test
    void resolveMethods_preservesOrderMethodFirst() {
        JsonObject json = new JsonObject();
        json.addProperty("method", "testC");
        JsonArray arr = new JsonArray();
        arr.add("testA");
        arr.add("testB");
        json.add("methods", arr);
        List<String> result = RunTestTool.resolveMethods(new Args(json));
        assertEquals(List.of("testC", "testA", "testB"), result);
    }
}
