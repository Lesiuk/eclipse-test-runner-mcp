package uk.l3si.eclipse.mcp.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reflection-based helper that executes multiple specific test methods within a
 * single RemoteTestRunner JVM launch. Invoked by bytecode injected into
 * {@code RemoteTestRunner.run()} by {@link RunMethodTransformer}.
 *
 * <p>All access to Eclipse JUnit runtime classes is via reflection so this
 * module has no compile-time dependency on Eclipse.</p>
 */
public class MultiMethodRunner {

    private static final String RUNNER_CLASS_NAME =
            "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner";

    /**
     * Parses a comma-separated list of method names.
     */
    static String[] parseMethods(String value) {
        if (value == null || value.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Main entry point called by the injected bytecode. Orchestrates
     * loading and running multiple test methods via reflection on the
     * RemoteTestRunner instance.
     */
    public static void execute(Object runner) throws Exception {
        if (runner == null) {
            throw new IllegalArgumentException("Runner must not be null");
        }

        String methodsProp = System.getProperty(RunMethodTransformer.PROPERTY_NAME);
        String[] methods = parseMethods(methodsProp);
        if (methods.length == 0) {
            throw new IllegalStateException(
                    "System property '" + RunMethodTransformer.PROPERTY_NAME
                            + "' is empty or not set");
        }

        Class<?> runnerClass = Class.forName(RUNNER_CLASS_NAME);

        // 1. connect()
        Method connectMethod = findMethod(runnerClass, "connect");
        connectMethod.invoke(runner);

        // 2. getTestLoader() -> ITestLoader
        Method getTestLoaderMethod = findMethod(runnerClass, "getTestLoader");
        Object loader = getTestLoaderMethod.invoke(runner);

        // 3. Get fTestClassNames field -> String[]
        Field testClassNamesField = findField(runnerClass, "fTestClassNames");
        String[] testClassNames = (String[]) testClassNamesField.get(runner);

        // 4. Get fIncludeExcludeTags field -> String[][]
        Field tagsField = findField(runnerClass, "fIncludeExcludeTags");
        String[][] tags = (String[][]) tagsField.get(runner);

        // 5. loadClasses(String[]) -> Class<?>[]
        Method loadClassesMethod = findMethod(runnerClass, "loadClasses",
                String[].class);
        Class<?>[] classes = (Class<?>[]) loadClassesMethod.invoke(runner,
                (Object) testClassNames);

        // 6. For each method, call loader.loadTests(classes, method, null, null, tags, null, runner)
        // The last param type must be RemoteTestRunner, not the actual runtime class.
        Class<?> loaderClass = loader.getClass();
        Class<?> remoteTestRunnerType = Class.forName(RUNNER_CLASS_NAME);
        Method loadTestsMethod = findLoadTestsMethod(loaderClass,
                remoteTestRunnerType);

        Class<?> refClass = Class.forName(
                "org.eclipse.jdt.internal.junit.runner.ITestReference");

        List<Object> allRefs = new ArrayList<>();
        for (String method : methods) {
            Object[] refs = (Object[]) loadTestsMethod.invoke(loader,
                    classes, method, null, null, tags, null, runner);
            if (refs != null) {
                for (Object ref : refs) {
                    allRefs.add(ref);
                }
            }
        }

        // 7. Combine into typed array
        Object combined = Array.newInstance(refClass, allRefs.size());
        for (int i = 0; i < allRefs.size(); i++) {
            Array.set(combined, i, allRefs.get(i));
        }

        // 8. firstRunExecutionListener() -> listener
        Method firstRunListenerMethod = findMethod(runnerClass,
                "firstRunExecutionListener");
        Object listener = firstRunListenerMethod.invoke(runner);

        // 9. getClassifier() -> classifier
        Method getClassifierMethod = findMethod(runnerClass, "getClassifier");
        Object classifier = getClassifierMethod.invoke(runner);

        // 10. Create TestExecution
        Class<?> listenerType = Class.forName(
                "org.eclipse.jdt.internal.junit.runner.IListensToTestExecutions");
        Class<?> classifierType = Class.forName(
                "org.eclipse.jdt.internal.junit.runner.IClassifiesThrowables");
        Class<?> execClass = Class.forName(
                "org.eclipse.jdt.internal.junit.runner.TestExecution");
        Constructor<?> execCtor = execClass.getDeclaredConstructor(
                listenerType, classifierType);
        execCtor.setAccessible(true);
        Object execution = execCtor.newInstance(listener, classifier);

        // 11. Store execution in fExecution field
        Field executionField = findField(runnerClass, "fExecution");
        executionField.set(runner, execution);

        // 12. Count tests
        int testCount = 0;
        Method countTestCases = findMethod(refClass, "countTestCases");
        Object[] combinedArray = (Object[]) combined;
        for (Object ref : combinedArray) {
            testCount += (int) countTestCases.invoke(ref);
        }

        // 13. notifyTestRunStarted(count)
        Method notifyStarted = findMethod(runnerClass,
                "notifyTestRunStarted", int.class);
        notifyStarted.invoke(runner, testCount);

        // 14. Send trees: ref.sendTree(runner) — runner implements IVisitsTestTrees
        Class<?> visitorType = Class.forName(
                "org.eclipse.jdt.internal.junit.runner.IVisitsTestTrees");
        Method sendTree = findMethod(refClass, "sendTree", visitorType);
        for (Object ref : combinedArray) {
            sendTree.invoke(ref, runner);
        }

        // 15. execution.run(combined)
        long startTime = System.currentTimeMillis();
        Method runMethod = findMethod(execClass, "run",
                Array.newInstance(refClass, 0).getClass());
        runMethod.invoke(execution, combined);
        long elapsedMs = System.currentTimeMillis() - startTime;

        // 16. notifyListenersOfTestEnd(execution, elapsedMs)
        // The second parameter is actually a long
        Method notifyEnd = findMethod(runnerClass,
                "notifyListenersOfTestEnd",
                execClass, long.class);
        notifyEnd.invoke(runner, execution, elapsedMs);

        // 17. Check fKeepAlive -> waitForReruns() if needed
        Field keepAliveField = findField(runnerClass, "fKeepAlive");
        boolean keepAlive = keepAliveField.getBoolean(runner);
        if (keepAlive) {
            Method waitForReruns = findMethod(runnerClass, "waitForReruns");
            waitForReruns.invoke(runner);
        }

        // 18. shutDown()
        Method shutDown = findMethod(runnerClass, "shutDown");
        shutDown.invoke(runner);
    }

    /**
     * Finds the {@code loadTests} method on the ITestLoader implementation,
     * matching the expected signature with the RemoteTestRunner parameter type.
     */
    private static Method findLoadTestsMethod(Class<?> loaderClass,
                                              Class<?> runnerType) {
        for (Method m : loaderClass.getMethods()) {
            if (!"loadTests".equals(m.getName())) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 7 && params[6].isAssignableFrom(runnerType)) {
                m.setAccessible(true);
                return m;
            }
        }
        // Also check interfaces
        for (Class<?> iface : loaderClass.getInterfaces()) {
            for (Method m : iface.getMethods()) {
                if (!"loadTests".equals(m.getName())) {
                    continue;
                }
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 7) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException(
                "Cannot find loadTests method on " + loaderClass.getName());
    }

    /**
     * Finds a method by name and parameter types, walking up the class
     * hierarchy. Sets the method accessible before returning.
     */
    static Method findMethod(Class<?> clazz, String name,
                             Class<?>... paramTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        // Also check interfaces (for ITestReference etc.)
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                Method m = iface.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                // continue
            }
        }
        throw new IllegalStateException(
                "Cannot find method " + name + " on " + clazz.getName());
    }

    /**
     * Finds a field by name, walking up the class hierarchy. Sets the field
     * accessible before returning.
     */
    static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException(
                "Cannot find field " + name + " on " + clazz.getName());
    }
}
