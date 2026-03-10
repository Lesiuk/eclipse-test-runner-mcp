package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TerminateToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(TerminateTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nullConfigNotMatchedByName() throws Exception {
        ILaunch launch = mock(ILaunch.class);
        when(launch.isTerminated()).thenReturn(false);
        when(launch.getLaunchConfiguration()).thenReturn(null);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            TerminateTool tool = new TerminateTool();
            JsonObject args = new JsonObject();
            args.addProperty("name", "MyApp");
            JsonObject result = executeAndSerialize(tool, args);

            assertEquals(0, result.get("terminated").getAsInt());
            verify(launch, never()).terminate();
        }
    }

    @Test
    void terminateAllIncludesNullConfigLaunches() throws Exception {
        ILaunch launchNoConfig = mock(ILaunch.class);
        when(launchNoConfig.isTerminated()).thenReturn(false);
        when(launchNoConfig.getLaunchConfiguration()).thenReturn(null);

        ILaunch launchWithConfig = mock(ILaunch.class);
        when(launchWithConfig.isTerminated()).thenReturn(false);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        when(config.getName()).thenReturn("App");
        when(launchWithConfig.getLaunchConfiguration()).thenReturn(config);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launchNoConfig, launchWithConfig});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            TerminateTool tool = new TerminateTool();
            JsonObject result = executeAndSerialize(tool, null); // terminate all

            assertEquals(2, result.get("terminated").getAsInt());
            verify(launchNoConfig).terminate();
            verify(launchWithConfig).terminate();
        }
    }

    @Test
    void terminateByNameMatchesCorrectly() throws Exception {
        ILaunch launch1 = mock(ILaunch.class);
        when(launch1.isTerminated()).thenReturn(false);
        ILaunchConfiguration config1 = mock(ILaunchConfiguration.class);
        when(config1.getName()).thenReturn("AppA");
        when(launch1.getLaunchConfiguration()).thenReturn(config1);

        ILaunch launch2 = mock(ILaunch.class);
        when(launch2.isTerminated()).thenReturn(false);
        ILaunchConfiguration config2 = mock(ILaunchConfiguration.class);
        when(config2.getName()).thenReturn("AppB");
        when(launch2.getLaunchConfiguration()).thenReturn(config2);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch1, launch2});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            TerminateTool tool = new TerminateTool();
            JsonObject args = new JsonObject();
            args.addProperty("name", "AppB");
            JsonObject result = executeAndSerialize(tool, args);

            assertEquals(1, result.get("terminated").getAsInt());
            verify(launch1, never()).terminate();
            verify(launch2).terminate();
        }
    }

    @Test
    void alreadyTerminatedLaunchesSkipped() throws Exception {
        ILaunch launch = mock(ILaunch.class);
        when(launch.isTerminated()).thenReturn(true);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        when(config.getName()).thenReturn("App");
        when(launch.getLaunchConfiguration()).thenReturn(config);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            TerminateTool tool = new TerminateTool();
            JsonObject result = executeAndSerialize(tool, null);

            assertEquals(0, result.get("terminated").getAsInt());
            verify(launch, never()).terminate();
        }
    }
}
