package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestLaunchHelperTest {

    private ILaunch mockJUnitLaunch(String name, boolean terminated) throws Exception {
        ILaunch launch = mock(ILaunch.class);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(type.getIdentifier()).thenReturn("org.eclipse.jdt.junit.launchconfig");
        when(config.getName()).thenReturn(name);
        when(config.getType()).thenReturn(type);
        when(launch.getLaunchConfiguration()).thenReturn(config);
        when(launch.isTerminated()).thenReturn(terminated);
        return launch;
    }

    @Test
    void checkNoTestRunning_noLaunches_passes() throws Exception {
        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);
            assertDoesNotThrow(() -> TestLaunchHelper.checkNoTestRunning());
        }
    }

    @Test
    void checkNoTestRunning_terminatedLaunchesIgnored() throws Exception {
        ILaunch terminated1 = mockJUnitLaunch("TestA", true);
        ILaunch terminated2 = mockJUnitLaunch("TestB", true);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{terminated1, terminated2});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            assertDoesNotThrow(() -> TestLaunchHelper.checkNoTestRunning());
            verify(manager, never()).removeLaunches(any());
        }
    }

    @Test
    void checkNoTestRunning_runningLaunchThrows() throws Exception {
        ILaunch running = mockJUnitLaunch("MyTests", false);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{running});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> TestLaunchHelper.checkNoTestRunning());
            assertTrue(ex.getMessage().contains("MyTests"));
        }
    }

    @Test
    void checkNoTestRunning_skipsTerminatedAndThrowsForRunning() throws Exception {
        ILaunch terminated = mockJUnitLaunch("OldTest", true);
        ILaunch running = mockJUnitLaunch("ActiveTest", false);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{terminated, running});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> TestLaunchHelper.checkNoTestRunning());
            assertTrue(ex.getMessage().contains("ActiveTest"));
            verify(manager, never()).removeLaunches(any());
        }
    }

    @Test
    void checkNoTestRunning_nonJUnitLaunchesIgnored() throws Exception {
        ILaunch nonJUnit = mock(ILaunch.class);
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(type.getIdentifier()).thenReturn("org.eclipse.debug.core.groups");
        when(config.getType()).thenReturn(type);
        when(nonJUnit.getLaunchConfiguration()).thenReturn(config);
        when(nonJUnit.isTerminated()).thenReturn(false);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{nonJUnit});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);
            assertDoesNotThrow(() -> TestLaunchHelper.checkNoTestRunning());
        }
    }

    @Test
    void checkNoTestRunning_nullConfigLaunchSkipped() throws Exception {
        ILaunch noConfig = mock(ILaunch.class);
        when(noConfig.getLaunchConfiguration()).thenReturn(null);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{noConfig});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);
            assertDoesNotThrow(() -> TestLaunchHelper.checkNoTestRunning());
        }
    }

    @Test
    void validMethodDoesNotThrow() throws Exception {
        IType type = mock(IType.class);
        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("testAdd");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        assertDoesNotThrow(() ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "testAdd"));
    }

    @Test
    void invalidMethodThrowsWithAvailableMethods() throws Exception {
        IType type = mock(IType.class);
        IMethod method1 = mock(IMethod.class);
        when(method1.getElementName()).thenReturn("testAdd");
        IMethod method2 = mock(IMethod.class);
        when(method2.getElementName()).thenReturn("testSubtract");
        when(type.getMethods()).thenReturn(new IMethod[]{method1, method2});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "nonExistent"));
        assertTrue(ex.getMessage().contains("nonExistent"));
        assertTrue(ex.getMessage().contains("not found"));
        assertTrue(ex.getMessage().contains("testAdd"));
        assertTrue(ex.getMessage().contains("testSubtract"));
    }

    @Test
    void noMethodsInClassShowsEmptyList() throws Exception {
        IType type = mock(IType.class);
        when(type.getMethods()).thenReturn(new IMethod[]{});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.EmptyTest", "testSomething"));
        assertTrue(ex.getMessage().contains("testSomething"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void matchesExactMethodNameOnly() throws Exception {
        IType type = mock(IType.class);
        IMethod method = mock(IMethod.class);
        when(method.getElementName()).thenReturn("testAddition");
        when(type.getMethods()).thenReturn(new IMethod[]{method});

        // "testAdd" should NOT match "testAddition"
        assertThrows(IllegalArgumentException.class, () ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "testAdd"));

        // exact match should work
        assertDoesNotThrow(() ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.FooTest", "testAddition"));
    }
}
