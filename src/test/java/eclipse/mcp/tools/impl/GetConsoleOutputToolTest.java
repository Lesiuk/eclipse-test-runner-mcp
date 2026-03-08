package eclipse.mcp.tools.impl;

import eclipse.mcp.tools.Args;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GetConsoleOutputToolTest {

    private static final Gson GSON = new Gson();

    private JsonObject executeAndSerialize(GetConsoleOutputTool tool, JsonObject args) throws Exception {
        return GSON.toJsonTree(tool.execute(new Args(args))).getAsJsonObject();
    }

    @Test
    void nullConfigSkippedWhenSearchingByName() throws Exception {
        ILaunch launch = mock(ILaunch.class);
        when(launch.getLaunchConfiguration()).thenReturn(null);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            GetConsoleOutputTool tool = new GetConsoleOutputTool();
            JsonObject args = new JsonObject();
            args.addProperty("name", "MyApp");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> tool.execute(new Args(args)));
            assertTrue(ex.getMessage().contains("No launch found for: MyApp"));
        }
    }

    @Test
    void nullConfigMatchedForMostRecent() throws Exception {
        ILaunch launch = mock(ILaunch.class);
        when(launch.getLaunchConfiguration()).thenReturn(null);
        when(launch.isTerminated()).thenReturn(true);

        IProcess process = mock(IProcess.class);
        IStreamsProxy proxy = mock(IStreamsProxy.class);
        IStreamMonitor outMonitor = mock(IStreamMonitor.class);
        IStreamMonitor errMonitor = mock(IStreamMonitor.class);
        when(outMonitor.getContents()).thenReturn("hello");
        when(errMonitor.getContents()).thenReturn("");
        when(proxy.getOutputStreamMonitor()).thenReturn(outMonitor);
        when(proxy.getErrorStreamMonitor()).thenReturn(errMonitor);
        when(process.getStreamsProxy()).thenReturn(proxy);
        when(launch.getProcesses()).thenReturn(new IProcess[]{process});

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            GetConsoleOutputTool tool = new GetConsoleOutputTool();
            JsonObject result = executeAndSerialize(tool, new JsonObject());

            assertEquals("unknown", result.get("configName").getAsString());
            assertEquals("hello", result.get("stdout").getAsString());
            assertTrue(result.get("terminated").getAsBoolean());
        }
    }

    @Test
    void configNameReturnedWhenPresent() throws Exception {
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        when(config.getName()).thenReturn("MyApp");
        when(launch.getLaunchConfiguration()).thenReturn(config);
        when(launch.isTerminated()).thenReturn(false);

        IProcess process = mock(IProcess.class);
        IStreamsProxy proxy = mock(IStreamsProxy.class);
        IStreamMonitor outMonitor = mock(IStreamMonitor.class);
        IStreamMonitor errMonitor = mock(IStreamMonitor.class);
        when(outMonitor.getContents()).thenReturn("output");
        when(errMonitor.getContents()).thenReturn("error");
        when(proxy.getOutputStreamMonitor()).thenReturn(outMonitor);
        when(proxy.getErrorStreamMonitor()).thenReturn(errMonitor);
        when(process.getStreamsProxy()).thenReturn(proxy);
        when(launch.getProcesses()).thenReturn(new IProcess[]{process});

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            GetConsoleOutputTool tool = new GetConsoleOutputTool();
            JsonObject args = new JsonObject();
            args.addProperty("name", "MyApp");
            JsonObject result = executeAndSerialize(tool, args);

            assertEquals("MyApp", result.get("configName").getAsString());
            assertEquals("output", result.get("stdout").getAsString());
            assertEquals("error", result.get("stderr").getAsString());
            assertFalse(result.get("terminated").getAsBoolean());
        }
    }

    @Test
    void noLaunchesThrowsException() throws Exception {
        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            GetConsoleOutputTool tool = new GetConsoleOutputTool();
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> tool.execute(new Args(null)));
            assertTrue(ex.getMessage().contains("No launches found"));
        }
    }
}
