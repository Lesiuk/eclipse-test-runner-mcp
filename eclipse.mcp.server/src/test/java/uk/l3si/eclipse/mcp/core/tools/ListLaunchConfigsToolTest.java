package uk.l3si.eclipse.mcp.core.tools;

import uk.l3si.eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ListLaunchConfigsToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(ListLaunchConfigsTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args), message -> {})).getAsJsonObject();
    }

    private ILaunchConfiguration mockJUnitConfig(String name, String typeName) throws Exception {
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(type.getIdentifier()).thenReturn("org.eclipse.jdt.junit.launchconfig");
        when(type.getName()).thenReturn(typeName);
        when(config.getName()).thenReturn(name);
        when(config.getType()).thenReturn(type);
        return config;
    }

    private ILaunchConfiguration mockNonJUnitConfig(String name) throws Exception {
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(type.getIdentifier()).thenReturn("org.eclipse.jdt.launching.localJavaApplication");
        when(type.getName()).thenReturn("Java Application");
        when(config.getName()).thenReturn(name);
        when(config.getType()).thenReturn(type);
        return config;
    }

    private MockedStatic<DebugPlugin> mockDebugPlugin(ILaunchConfiguration... configs) throws Exception {
        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunchConfigurations()).thenReturn(configs);

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class);
        mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);
        return mocked;
    }

    @Test
    void nameIsListTestConfigs() {
        assertEquals("list_test_configs", new ListLaunchConfigsTool().getName());
    }

    @Test
    void noConfigs() throws Exception {
        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin()) {
            ListLaunchConfigsTool tool = new ListLaunchConfigsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray configs = result.getAsJsonArray("testConfigurations");
            assertEquals(0, configs.size());
        }
    }

    @Test
    void filtersToJUnitOnly() throws Exception {
        ILaunchConfiguration junitConfig = mockJUnitConfig("MyTest", "JUnit");
        ILaunchConfiguration javaConfig = mockNonJUnitConfig("MyApp");

        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin(junitConfig, javaConfig)) {
            ListLaunchConfigsTool tool = new ListLaunchConfigsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray configs = result.getAsJsonArray("testConfigurations");
            assertEquals(1, configs.size());
            assertEquals("MyTest", configs.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("JUnit", configs.get(0).getAsJsonObject().get("type").getAsString());
        }
    }

    @Test
    void typeExceptionFallsBackToUnknown() throws Exception {
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        when(config.getName()).thenReturn("BrokenConfig");
        // getType() works for the filter check (isJUnitConfig)
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(type.getIdentifier()).thenReturn("org.eclipse.jdt.junit.launchconfig");
        when(config.getType()).thenReturn(type);
        // But getType().getName() throws for the toConfigInfo call
        when(type.getName()).thenThrow(new RuntimeException("type error"));

        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin(config)) {
            ListLaunchConfigsTool tool = new ListLaunchConfigsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray configs = result.getAsJsonArray("testConfigurations");
            assertEquals(1, configs.size());
            assertEquals("BrokenConfig", configs.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("unknown", configs.get(0).getAsJsonObject().get("type").getAsString());
        }
    }

    @Test
    void multipleJUnitConfigs() throws Exception {
        ILaunchConfiguration config1 = mockJUnitConfig("TestA", "JUnit");
        ILaunchConfiguration config2 = mockJUnitConfig("TestB", "JUnit Plug-in Test");

        try (MockedStatic<DebugPlugin> dp = mockDebugPlugin(config1, config2)) {
            ListLaunchConfigsTool tool = new ListLaunchConfigsTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray configs = result.getAsJsonArray("testConfigurations");
            assertEquals(2, configs.size());
            assertEquals("TestA", configs.get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("JUnit", configs.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("TestB", configs.get(1).getAsJsonObject().get("name").getAsString());
            assertEquals("JUnit Plug-in Test", configs.get(1).getAsJsonObject().get("type").getAsString());
        }
    }
}
