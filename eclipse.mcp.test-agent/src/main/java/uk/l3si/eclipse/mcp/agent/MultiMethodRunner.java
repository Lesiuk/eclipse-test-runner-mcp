package uk.l3si.eclipse.mcp.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Orchestrates running multiple specific test methods within a single
 * Eclipse JUnit test JVM launch. Called by bytecode injected into
 * {@code RemoteTestRunner.run()} by {@link RunMethodTransformer}.
 *
 * <p>All Eclipse JUnit runtime access is via reflection — this module
 * has no compile-time dependency on Eclipse.</p>
 */
public class MultiMethodRunner {

    // -- Eclipse class names --------------------------------------------------

    private static final String RUNNER       = "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner";
    private static final String TEST_REF     = "org.eclipse.jdt.internal.junit.runner.ITestReference";
    private static final String VISITOR      = "org.eclipse.jdt.internal.junit.runner.IVisitsTestTrees";
    private static final String EXEC         = "org.eclipse.jdt.internal.junit.runner.TestExecution";
    private static final String LISTENER     = "org.eclipse.jdt.internal.junit.runner.IListensToTestExecutions";
    private static final String CLASSIFIER   = "org.eclipse.jdt.internal.junit.runner.IClassifiesThrowables";

    // -- Loader → reference class mappings ------------------------------------

    private static final String JUNIT5_LOADER = "org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader";
    private static final String JUNIT5_REF    = "org.eclipse.jdt.internal.junit5.runner.JUnit5TestReference";
    private static final String JUNIT6_LOADER = "org.eclipse.jdt.internal.junit6.runner.JUnit6TestLoader";
    private static final String JUNIT6_REF    = "org.eclipse.jdt.internal.junit6.runner.JUnit6TestReference";
    private static final String JUNIT4_LOADER = "org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader";

    // -- Public entry point ---------------------------------------------------

    /**
     * Parse comma-separated method names, trimming whitespace and filtering blanks.
     */
    static String[] parseMethods(String value) {
        if (value == null || value.isBlank()) return new String[0];
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Main entry point called by the injected bytecode.
     *
     * @param runner the RemoteTestRunner instance (passed as Object to
     *               avoid compile-time dependency)
     */
    public static void execute(Object runner) throws Exception {
        if (runner == null) {
            throw new IllegalArgumentException("Runner must not be null");
        }
        String[] methods = parseMethods(System.getProperty(RunMethodTransformer.PROPERTY_NAME));
        if (methods.length == 0) {
            throw new IllegalStateException(
                    "System property '" + RunMethodTransformer.PROPERTY_NAME + "' is empty or not set");
        }

        Class<?> runnerClass = Class.forName(RUNNER);

        // Phase 1: connect and gather context
        ReflectionUtils.findMethod(runnerClass, "connect").invoke(runner);

        Object   loader         = ReflectionUtils.findMethod(runnerClass, "getTestLoader").invoke(runner);
        String[] testClassNames = (String[]) ReflectionUtils.findField(runnerClass, "fTestClassNames").get(runner);
        String[][] tags         = (String[][]) ReflectionUtils.findField(runnerClass, "fIncludeExcludeTags").get(runner);
        Class<?>[] classes      = (Class<?>[]) ReflectionUtils.findMethod(runnerClass, "loadClasses", String[].class)
                                        .invoke(runner, (Object) testClassNames);

        // Phase 2: build test references (loader-specific for proper JUnit view display)
        List<Object> refs = buildTestRefs(loader, testClassNames[0], methods, tags, classes, runner);

        // Phase 3: execute the test session
        runTestSession(runner, runnerClass, refs);
    }

    // -- Test reference building (per loader) ---------------------------------

    private static List<Object> buildTestRefs(Object loader, String className,
            String[] methods, String[][] tags, Class<?>[] classes,
            Object runner) throws Exception {
        String loaderName = loader.getClass().getName();

        if (JUNIT5_LOADER.equals(loaderName)) {
            return buildPlatformRefs(loader, className, methods, tags, runner, JUNIT5_REF);
        }
        if (JUNIT6_LOADER.equals(loaderName)) {
            return buildPlatformRefs(loader, className, methods, tags, runner, JUNIT6_REF);
        }
        if (JUNIT4_LOADER.equals(loaderName)) {
            return buildJUnit4Refs(loader, methods, classes);
        }
        return buildFallbackRefs(loader, classes, methods, tags, runner);
    }

    /** JUnit 5/6: single LauncherDiscoveryRequest with multiple method selectors. */
    private static List<Object> buildPlatformRefs(Object loader, String className,
            String[] methods, String[][] tags, Object runner,
            String testRefClassName) throws Exception {
        ClassLoader cl = loader.getClass().getClassLoader();

        // Build selectors: DiscoverySelectors.selectMethod("class#method") for each
        Class<?> selectorClass = cl.loadClass("org.junit.platform.engine.DiscoverySelector");
        Method selectMethod = cl.loadClass("org.junit.platform.engine.discovery.DiscoverySelectors")
                .getMethod("selectMethod", String.class);

        Object selectors = Array.newInstance(selectorClass, methods.length);
        for (int i = 0; i < methods.length; i++) {
            Array.set(selectors, i, selectMethod.invoke(null, className + "#" + methods[i]));
        }

        // Build request
        Class<?> builderClass = cl.loadClass(
                "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
        Object builder = builderClass.getMethod("request").invoke(null);
        builderClass.getMethod("selectors", selectors.getClass()).invoke(builder, (Object) selectors);
        applyTagFilters(loader, builder, builderClass, cl, tags);
        Object request = builderClass.getMethod("build").invoke(builder);

        // Create test reference using the loader's Launcher and our runner
        Object launcher = ReflectionUtils.findField(loader.getClass(), "fLauncher").get(loader);
        Constructor<?> refCtor = cl.loadClass(testRefClassName).getDeclaredConstructor(
                cl.loadClass("org.junit.platform.launcher.LauncherDiscoveryRequest"),
                cl.loadClass("org.junit.platform.launcher.Launcher"),
                Class.forName(RUNNER));
        refCtor.setAccessible(true);

        return List.of(refCtor.newInstance(request, launcher, runner));
    }

    private static void applyTagFilters(Object loader, Object builder,
            Class<?> builderClass, ClassLoader cl, String[][] tags) {
        if (tags == null) return;
        try {
            Method getTagFilters = loader.getClass().getDeclaredMethod("getTagFilters", String[][].class);
            getTagFilters.setAccessible(true);
            Object[] filters = (Object[]) getTagFilters.invoke(loader, (Object) tags);
            if (filters != null && filters.length > 0) {
                Class<?> filterType = cl.loadClass("org.junit.platform.launcher.Filter");
                Object filterArray = Array.newInstance(filterType, filters.length);
                for (int i = 0; i < filters.length; i++) Array.set(filterArray, i, filters[i]);
                builderClass.getMethod("filters", filterArray.getClass()).invoke(builder, (Object) filterArray);
            }
        } catch (NoSuchMethodException ignored) {
            // getTagFilters not available on this loader version
        } catch (Exception e) {
            System.err.println("[eclipse-mcp-agent] Warning: failed to apply tag filters: " + e.getMessage());
        }
    }

    /** JUnit 4/SWTBot: Request filtered with an ASM-generated multi-method Filter. */
    private static List<Object> buildJUnit4Refs(Object loader, String[] methods,
            Class<?>[] classes) throws Exception {
        ClassLoader cl = loader.getClass().getClassLoader();
        Class<?> testClass = classes[0];

        // Create filtered request
        Class<?> requestClass = cl.loadClass("org.junit.runner.Request");
        Object request = requestClass.getMethod("classWithoutSuiteMethod", Class.class).invoke(null, testClass);

        Class<?> filterClass = cl.loadClass("org.junit.runner.manipulation.Filter");
        Object filter = MultiMethodFilterGenerator.create(cl, filterClass, methods);
        Object filtered = requestClass.getMethod("filterWith", filterClass).invoke(request, filter);

        // Get runner and description for the reference
        Object filteredRunner = requestClass.getMethod("getRunner").invoke(filtered);
        Class<?> runnerType = cl.loadClass("org.junit.runner.Runner");
        Object rootDesc = runnerType.getMethod("getDescription").invoke(filteredRunner);

        // Create JUnit4TestReference(Runner, Description)
        Class<?> refClass = cl.loadClass("org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference");
        Constructor<?> refCtor = refClass.getDeclaredConstructor(runnerType,
                cl.loadClass("org.junit.runner.Description"));
        refCtor.setAccessible(true);

        return List.of(refCtor.newInstance(filteredRunner, rootDesc));
    }

    /** Fallback for unknown loaders: one loadTests call per method (duplicates class in JUnit view). */
    private static List<Object> buildFallbackRefs(Object loader, Class<?>[] classes,
            String[] methods, String[][] tags, Object runner) throws Exception {
        Method loadTests = ReflectionUtils.findLoadTestsMethod(loader.getClass(), Class.forName(RUNNER));
        List<Object> refs = new ArrayList<>();
        for (String method : methods) {
            Object[] result = (Object[]) loadTests.invoke(loader, classes, method, null, null, tags, null, runner);
            if (result != null) {
                for (Object ref : result) refs.add(ref);
            }
        }
        return refs;
    }

    // -- Test session execution ------------------------------------------------

    private static void runTestSession(Object runner, Class<?> runnerClass,
            List<Object> refs) throws Exception {
        Class<?> refClass   = Class.forName(TEST_REF);
        Class<?> execClass  = Class.forName(EXEC);
        Class<?> visitorType = Class.forName(VISITOR);

        // Create ITestReference[] array
        Object combined = Array.newInstance(refClass, refs.size());
        for (int i = 0; i < refs.size(); i++) Array.set(combined, i, refs.get(i));
        Object[] combinedArray = (Object[]) combined;

        // Create TestExecution
        Object listener   = ReflectionUtils.findMethod(runnerClass, "firstRunExecutionListener").invoke(runner);
        Object classifier = ReflectionUtils.findMethod(runnerClass, "getClassifier").invoke(runner);
        Constructor<?> execCtor = execClass.getDeclaredConstructor(Class.forName(LISTENER), Class.forName(CLASSIFIER));
        execCtor.setAccessible(true);
        Object execution = execCtor.newInstance(listener, classifier);
        ReflectionUtils.findField(runnerClass, "fExecution").set(runner, execution);

        // Count tests
        Method countTestCases = ReflectionUtils.findMethod(refClass, "countTestCases");
        int testCount = 0;
        for (Object ref : combinedArray) testCount += (int) countTestCases.invoke(ref);

        // Notify start, send trees, run, notify end
        ReflectionUtils.findMethod(runnerClass, "notifyTestRunStarted", int.class).invoke(runner, testCount);

        Method sendTree = ReflectionUtils.findMethod(refClass, "sendTree", visitorType);
        for (Object ref : combinedArray) sendTree.invoke(ref, runner);

        long start = System.currentTimeMillis();
        ReflectionUtils.findMethod(execClass, "run", Array.newInstance(refClass, 0).getClass())
                .invoke(execution, combined);
        long elapsed = System.currentTimeMillis() - start;

        ReflectionUtils.findMethod(runnerClass, "notifyListenersOfTestEnd", execClass, long.class)
                .invoke(runner, execution, elapsed);

        // Keepalive / shutdown
        if (ReflectionUtils.findField(runnerClass, "fKeepAlive").getBoolean(runner)) {
            ReflectionUtils.findMethod(runnerClass, "waitForReruns").invoke(runner);
        }
        ReflectionUtils.findMethod(runnerClass, "shutDown").invoke(runner);
    }
}
