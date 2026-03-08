package eclipse.mcp.tools.impl;

import eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ListLaunchesToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(ListLaunchesTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    private ILaunch mockJUnitLaunch(String name, String mode, boolean terminated) throws Exception {
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(type.getIdentifier()).thenReturn("org.eclipse.jdt.junit.launchconfig");
        when(config.getName()).thenReturn(name);
        when(config.getType()).thenReturn(type);
        when(launch.getLaunchConfiguration()).thenReturn(config);
        when(launch.getLaunchMode()).thenReturn(mode);
        when(launch.isTerminated()).thenReturn(terminated);
        return launch;
    }

    @Test
    void nullConfigLaunchIsSkipped() throws Exception {
        ILaunch junitLaunch = mockJUnitLaunch("MyTest", "run", false);

        ILaunch launchNoConfig = mock(ILaunch.class);
        when(launchNoConfig.getLaunchConfiguration()).thenReturn(null);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{junitLaunch, launchNoConfig});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            ListLaunchesTool tool = new ListLaunchesTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray runs = result.getAsJsonArray("testRuns");
            assertEquals(1, runs.size());
            assertEquals("MyTest", runs.get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void nonJUnitLaunchIsFiltered() throws Exception {
        ILaunch junitLaunch = mockJUnitLaunch("MyTest", "run", false);

        ILaunch javaLaunch = mock(ILaunch.class);
        ILaunchConfiguration javaConfig = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType javaType = mock(ILaunchConfigurationType.class);
        when(javaType.getIdentifier()).thenReturn("org.eclipse.jdt.launching.localJavaApplication");
        when(javaConfig.getName()).thenReturn("MyApp");
        when(javaConfig.getType()).thenReturn(javaType);
        when(javaLaunch.getLaunchConfiguration()).thenReturn(javaConfig);
        when(javaLaunch.getLaunchMode()).thenReturn("run");
        when(javaLaunch.isTerminated()).thenReturn(false);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{junitLaunch, javaLaunch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            ListLaunchesTool tool = new ListLaunchesTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            JsonArray runs = result.getAsJsonArray("testRuns");
            assertEquals(1, runs.size());
            assertEquals("MyTest", runs.get(0).getAsJsonObject().get("name").getAsString());
        }
    }

    @Test
    void emptyLaunchesReturnsEmptyArray() throws Exception {
        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            ListLaunchesTool tool = new ListLaunchesTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());
            assertEquals(0, result.getAsJsonArray("testRuns").size());
        }
    }
}
