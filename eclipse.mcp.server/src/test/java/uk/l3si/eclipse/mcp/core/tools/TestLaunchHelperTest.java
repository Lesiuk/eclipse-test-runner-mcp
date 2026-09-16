package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.junit.JUnitCore;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void ensureNoTestRunning_waitsForLaunchBeingTerminated() throws Exception {
        // A launch that is mid-termination: isTerminated() flips to true shortly after,
        // simulating a concurrent 'terminate' call stopping the running test.
        ILaunch launch = mockJUnitLaunch("MyTests", false);
        AtomicBoolean terminated = new AtomicBoolean(false);
        when(launch.isTerminated()).thenAnswer(inv -> terminated.get());

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{launch});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "terminate-simulator");
            t.setDaemon(true);
            return t;
        });
        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);
            scheduler.schedule(() -> terminated.set(true), 150, TimeUnit.MILLISECONDS);

            long start = System.nanoTime();
            // Should wait for the in-flight termination instead of failing immediately.
            assertDoesNotThrow(() -> TestLaunchHelper.ensureNoTestRunning(5_000));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue(terminated.get(), "should have observed the launch terminating");
            assertTrue(elapsedMs >= 100,
                    "should have waited for the in-flight termination, elapsed=" + elapsedMs + "ms");
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    void ensureNoTestRunning_throwsAfterGracePeriodIfStillRunning() throws Exception {
        // A launch that never terminates — no concurrent 'terminate' is in flight.
        ILaunch running = mockJUnitLaunch("StuckTest", false);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunches()).thenReturn(new ILaunch[]{running});

        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            long start = System.nanoTime();
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> TestLaunchHelper.ensureNoTestRunning(300));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertTrue(ex.getMessage().contains("StuckTest"));
            assertTrue(ex.getMessage().contains("terminate"));
            assertTrue(elapsedMs >= 250,
                    "should have waited the grace period before failing, elapsed=" + elapsedMs + "ms");
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
    void noMethodsInClassSkipsValidation() throws Exception {
        IType type = mock(IType.class);
        when(type.getMethods()).thenReturn(new IMethod[]{});

        // When getMethods() returns empty (e.g. binary type without source), validation is skipped
        assertDoesNotThrow(() ->
                TestLaunchHelper.validateMethodOnType(type, "com.test.EmptyTest", "testSomething"));
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

    @Test
    void findTestPackage_returnsExistingPackageFromSourceRoot() throws Exception {
        IJavaProject javaProject = mock(IJavaProject.class);
        IPackageFragmentRoot sourceRoot = mock(IPackageFragmentRoot.class);
        IPackageFragment packageFragment = mock(IPackageFragment.class);
        when(sourceRoot.getKind()).thenReturn(IPackageFragmentRoot.K_SOURCE);
        when(sourceRoot.getPackageFragment("com.example.unit")).thenReturn(packageFragment);
        when(packageFragment.exists()).thenReturn(true);
        when(javaProject.getPackageFragmentRoots()).thenReturn(new IPackageFragmentRoot[]{sourceRoot});

        assertSame(packageFragment,
                TestLaunchHelper.findTestPackage(javaProject, "com.example.unit"));
    }

    @Test
    void findTestPackage_prefersSourcePackageContainingJUnitTests() throws Exception {
        IJavaProject javaProject = mock(IJavaProject.class);
        IPackageFragmentRoot productionRoot = mock(IPackageFragmentRoot.class);
        IPackageFragmentRoot testRoot = mock(IPackageFragmentRoot.class);
        IPackageFragment productionPackage = mock(IPackageFragment.class);
        IPackageFragment testPackage = mock(IPackageFragment.class);
        when(productionRoot.getKind()).thenReturn(IPackageFragmentRoot.K_SOURCE);
        when(testRoot.getKind()).thenReturn(IPackageFragmentRoot.K_SOURCE);
        when(productionRoot.getPackageFragment("com.example.unit")).thenReturn(productionPackage);
        when(testRoot.getPackageFragment("com.example.unit")).thenReturn(testPackage);
        when(productionPackage.exists()).thenReturn(true);
        when(testPackage.exists()).thenReturn(true);
        when(javaProject.getPackageFragmentRoots()).thenReturn(
                new IPackageFragmentRoot[]{productionRoot, testRoot});

        try (MockedStatic<JUnitCore> junitCore = mockStatic(JUnitCore.class)) {
            junitCore.when(() -> JUnitCore.findTestTypes(productionPackage, null))
                    .thenReturn(new IType[]{});
            junitCore.when(() -> JUnitCore.findTestTypes(testPackage, null))
                    .thenReturn(new IType[]{mock(IType.class)});

            assertSame(testPackage,
                    TestLaunchHelper.findTestPackage(javaProject, "com.example.unit"));
        }
    }

    @Test
    void findTestPackage_ignoresPackagesOnlyPresentInBinaryRoots() throws Exception {
        IJavaProject javaProject = mock(IJavaProject.class);
        IPackageFragmentRoot binaryRoot = mock(IPackageFragmentRoot.class);
        IPackageFragment packageFragment = mock(IPackageFragment.class);
        when(binaryRoot.getKind()).thenReturn(IPackageFragmentRoot.K_BINARY);
        when(binaryRoot.getPackageFragment("com.example.unit")).thenReturn(packageFragment);
        when(packageFragment.exists()).thenReturn(true);
        when(javaProject.getPackageFragmentRoots()).thenReturn(new IPackageFragmentRoot[]{binaryRoot});

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TestLaunchHelper.findTestPackage(javaProject, "com.example.unit"));

        assertTrue(exception.getMessage().contains("com.example.unit"));
        assertTrue(exception.getMessage().contains("source"));
    }

    @Test
    void configurePackageTarget_usesContainerAndClearsClassTarget() throws Exception {
        ILaunchConfigurationWorkingCopy workingCopy = mock(ILaunchConfigurationWorkingCopy.class);

        TestLaunchHelper.configurePackageTarget(workingCopy, "=app/src<org.example.unit");

        verify(workingCopy).setAttribute("org.eclipse.jdt.junit.CONTAINER", "=app/src<org.example.unit");
        verify(workingCopy).setAttribute("org.eclipse.jdt.junit.TESTNAME", "");
        verify(workingCopy).removeAttribute("org.eclipse.jdt.launching.MAIN_TYPE");
    }

    @Test
    void launchTest_packageTargetRequiresProjectWhenConfigHasNone() throws Exception {
        ILaunchConfiguration config = mock(ILaunchConfiguration.class);
        ILaunchConfigurationType type = mock(ILaunchConfigurationType.class);
        when(config.getName()).thenReturn("Unit tests");
        when(config.getType()).thenReturn(type);
        when(type.getIdentifier()).thenReturn("org.eclipse.jdt.junit.launchconfig");
        when(config.getAttribute("org.eclipse.jdt.launching.PROJECT_ATTR", (String) null))
                .thenReturn(null);

        ILaunchManager manager = mock(ILaunchManager.class);
        when(manager.getLaunchConfigurations()).thenReturn(new ILaunchConfiguration[]{config});
        DebugPlugin debugPlugin = mock(DebugPlugin.class);
        when(debugPlugin.getLaunchManager()).thenReturn(manager);

        try (MockedStatic<DebugPlugin> mocked = mockStatic(DebugPlugin.class)) {
            mocked.when(DebugPlugin::getDefault).thenReturn(debugPlugin);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> TestLaunchHelper.launchTest(
                            "Unit tests", null, "com.example.unit", null,
                            null, "run", null, message -> { }));

            assertTrue(exception.getMessage().contains("requires a project"));
        }
    }

    @Test
    void launchTest_rejectsClassAndPackageTogetherBeforeConfigLookup() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> TestLaunchHelper.launchTest(
                        "Unit tests", "com.example.FooTest", "com.example.unit", null,
                        null, "run", null, message -> { }));

        assertTrue(exception.getMessage().contains("mutually exclusive"));
    }
}
