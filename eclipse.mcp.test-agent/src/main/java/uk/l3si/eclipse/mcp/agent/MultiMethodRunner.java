package uk.l3si.eclipse.mcp.agent;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

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

    private static final String JUNIT5_LOADER =
            "org.eclipse.jdt.internal.junit5.runner.JUnit5TestLoader";
    private static final String JUNIT5_REF =
            "org.eclipse.jdt.internal.junit5.runner.JUnit5TestReference";

    private static final String JUNIT6_LOADER =
            "org.eclipse.jdt.internal.junit6.runner.JUnit6TestLoader";
    private static final String JUNIT6_REF =
            "org.eclipse.jdt.internal.junit6.runner.JUnit6TestReference";

    private static final String JUNIT4_LOADER =
            "org.eclipse.jdt.internal.junit4.runner.JUnit4TestLoader";

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

        // 6. Build test references — try to create a single reference for
        //    all methods so the JUnit view groups them under one class node.
        Class<?> refClass = Class.forName(
                "org.eclipse.jdt.internal.junit.runner.ITestReference");

        String loaderClassName = loader.getClass().getName();
        String className = testClassNames[0];

        List<Object> allRefs;
        if (JUNIT5_LOADER.equals(loaderClassName)) {
            allRefs = buildPlatformRefs(loader, className, methods, tags, runner, JUNIT5_REF);
        } else if (JUNIT6_LOADER.equals(loaderClassName)) {
            allRefs = buildPlatformRefs(loader, className, methods, tags, runner, JUNIT6_REF);
        } else if (JUNIT4_LOADER.equals(loaderClassName)) {
            allRefs = buildJUnit4Refs(loader, className, methods, classes);
        } else {
            allRefs = buildFallbackRefs(loader, classes, methods, tags,
                    runner, runnerClass);
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

    // ------------------------------------------------------------------
    // JUnit 5: single LauncherDiscoveryRequest with multiple method selectors
    // ------------------------------------------------------------------

    private static List<Object> buildPlatformRefs(Object loader,
            String className, String[] methods, String[][] tags,
            Object runner, String testRefClassName) throws Exception {

        ClassLoader cl = loader.getClass().getClassLoader();

        // DiscoverySelectors.selectMethod(String)
        Class<?> selectorsClass = cl.loadClass(
                "org.junit.platform.engine.discovery.DiscoverySelectors");
        Method selectMethod = selectorsClass.getMethod("selectMethod",
                String.class);
        Class<?> selectorClass = cl.loadClass(
                "org.junit.platform.engine.DiscoverySelector");

        // Build selectors array
        Object selectorsArray = Array.newInstance(selectorClass, methods.length);
        for (int i = 0; i < methods.length; i++) {
            Object selector = selectMethod.invoke(null,
                    className + "#" + methods[i]);
            Array.set(selectorsArray, i, selector);
        }

        // LauncherDiscoveryRequestBuilder.request()
        Class<?> builderClass = cl.loadClass(
                "org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
        Method requestFactory = builderClass.getMethod("request");
        Object builder = requestFactory.invoke(null);

        // builder.selectors(DiscoverySelector[])
        Method selectorsMethod = builderClass.getMethod("selectors",
                selectorsArray.getClass());
        selectorsMethod.invoke(builder, selectorsArray);

        // Apply tag filters if present
        if (tags != null) {
            try {
                Method getTagFilters = loader.getClass().getDeclaredMethod(
                        "getTagFilters", String[][].class);
                getTagFilters.setAccessible(true);
                Object[] tagFilters = (Object[]) getTagFilters.invoke(loader,
                        (Object) tags);
                if (tagFilters != null && tagFilters.length > 0) {
                    Class<?> filterClass = cl.loadClass(
                            "org.junit.platform.launcher.Filter");
                    Object filtersArray = Array.newInstance(filterClass,
                            tagFilters.length);
                    for (int i = 0; i < tagFilters.length; i++) {
                        Array.set(filtersArray, i, tagFilters[i]);
                    }
                    Method filtersMethod = builderClass.getMethod("filters",
                            filtersArray.getClass());
                    filtersMethod.invoke(builder, filtersArray);
                }
            } catch (NoSuchMethodException e) {
                // getTagFilters not available, skip tag filtering
            }
        }

        // builder.build()
        Method buildMethod = builderClass.getMethod("build");
        Object request = buildMethod.invoke(builder);

        // Get fLauncher from the loader
        Field launcherField = findField(loader.getClass(), "fLauncher");
        Object launcher = launcherField.get(loader);

        // Get fRemoteTestRunner from the loader
        Field remoteRunnerField = findField(loader.getClass(),
                "fRemoteTestRunner");
        Object remoteRunner = remoteRunnerField.get(loader);

        // Create test reference (JUnit5TestReference or JUnit6TestReference)
        Class<?> requestType = cl.loadClass(
                "org.junit.platform.launcher.LauncherDiscoveryRequest");
        Class<?> launcherType = cl.loadClass(
                "org.junit.platform.launcher.Launcher");
        Class<?> rtrType = Class.forName(RUNNER_CLASS_NAME);
        Class<?> refClass = cl.loadClass(testRefClassName);

        Constructor<?> refCtor = refClass.getDeclaredConstructor(
                requestType, launcherType, rtrType);
        refCtor.setAccessible(true);
        Object ref = refCtor.newInstance(request, launcher, remoteRunner);

        List<Object> refs = new ArrayList<>();
        refs.add(ref);
        return refs;
    }

    // ------------------------------------------------------------------
    // JUnit 4: single Request with ASM-generated multi-method Filter
    // ------------------------------------------------------------------

    private static List<Object> buildJUnit4Refs(Object loader,
            String className, String[] methods,
            Class<?>[] classes) throws Exception {

        ClassLoader cl = loader.getClass().getClassLoader();
        Class<?> testClass = classes[0];

        // Request.classWithoutSuiteMethod(Class)
        Class<?> requestClass = cl.loadClass("org.junit.runner.Request");
        Method classWithoutSuite = requestClass.getMethod(
                "classWithoutSuiteMethod", Class.class);
        Object request = classWithoutSuite.invoke(null, testClass);

        // Generate and instantiate the multi-method filter
        Class<?> filterClass = cl.loadClass(
                "org.junit.runner.manipulation.Filter");
        Object filter = createMultiMethodFilter(cl, filterClass, methods);

        // request.filterWith(filter)
        Method filterWith = requestClass.getMethod("filterWith", filterClass);
        Object filteredRequest = filterWith.invoke(request, filter);

        // filteredRequest.getRunner()
        Method getRunner = requestClass.getMethod("getRunner");
        Object filteredRunner = getRunner.invoke(filteredRequest);

        // filteredRunner.getDescription()
        Class<?> runnerType = cl.loadClass("org.junit.runner.Runner");
        Method getDescription = runnerType.getMethod("getDescription");
        Object rootDescription = getDescription.invoke(filteredRunner);

        // Create JUnit4TestReference(Runner, Description)
        Class<?> descClass = cl.loadClass("org.junit.runner.Description");
        Class<?> refClass = cl.loadClass(
                "org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference");
        Constructor<?> refCtor = refClass.getDeclaredConstructor(
                runnerType, descClass);
        refCtor.setAccessible(true);
        Object ref = refCtor.newInstance(filteredRunner, rootDescription);

        List<Object> refs = new ArrayList<>();
        refs.add(ref);
        return refs;
    }

    /**
     * Generates an ASM-based subclass of {@code org.junit.runner.manipulation.Filter}
     * at runtime. The generated class filters test methods by name, keeping only
     * those in the provided set. Suite nodes (where getMethodName() returns null)
     * are always kept so the class container appears in the tree.
     */
    static Object createMultiMethodFilter(ClassLoader parentCl,
            Class<?> filterSuperClass,
            String[] methods) throws Exception {

        String superInternal = Type.getInternalName(filterSuperClass);
        String generatedName =
                "uk/l3si/eclipse/mcp/agent/generated/MultiMethodFilter";
        String setDesc = Type.getDescriptor(Set.class);
        String descriptionInternal = "org/junit/runner/Description";

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES
                | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generatedName, null,
                superInternal, null);

        // Field: private final Set methodNames
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "methodNames", setDesc, null, null).visitEnd();

        // Constructor(Set)
        {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                    "(" + setDesc + ")V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superInternal,
                    "<init>", "()V", false);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitFieldInsn(Opcodes.PUTFIELD, generatedName,
                    "methodNames", setDesc);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // shouldRun(Description): return desc.getMethodName() == null
        //                                || methodNames.contains(desc.getMethodName())
        {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "shouldRun",
                    "(L" + descriptionInternal + ";)Z", null, null);
            mv.visitCode();

            // String name = description.getMethodName()
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, descriptionInternal,
                    "getMethodName", "()Ljava/lang/String;", false);
            mv.visitVarInsn(Opcodes.ASTORE, 2);

            // if (name == null) return true
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            Label notNull = new Label();
            mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitInsn(Opcodes.IRETURN);

            // return methodNames.contains(name)
            mv.visitLabel(notNull);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(Opcodes.GETFIELD, generatedName,
                    "methodNames", setDesc);
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                    Type.getInternalName(Set.class), "contains",
                    "(Ljava/lang/Object;)Z", true);
            mv.visitInsn(Opcodes.IRETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // describe(): return "MultiMethodFilter"
        {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "describe",
                    "()Ljava/lang/String;", null, null);
            mv.visitCode();
            mv.visitLdcInsn("MultiMethodFilter");
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();
        byte[] bytecode = cw.toByteArray();

        // Define the class using a custom classloader parented by the
        // loader's classloader so it can see both Filter and Set.
        Class<?> filterImplClass = new ByteArrayClassLoader(parentCl)
                .defineClass(generatedName.replace('/', '.'), bytecode);

        Set<String> methodSet = new HashSet<>(Arrays.asList(methods));
        Constructor<?> ctor = filterImplClass.getConstructor(Set.class);
        return ctor.newInstance(methodSet);
    }

    /**
     * Simple classloader that defines a single class from a byte array.
     */
    private static class ByteArrayClassLoader extends ClassLoader {
        ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> defineClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    // ------------------------------------------------------------------
    // Fallback: per-method loadTests (produces duplicated class nodes)
    // ------------------------------------------------------------------

    private static List<Object> buildFallbackRefs(Object loader,
            Class<?>[] classes, String[] methods, String[][] tags,
            Object runner, Class<?> runnerClass) throws Exception {

        Class<?> loaderClass = loader.getClass();
        Class<?> remoteTestRunnerType = Class.forName(RUNNER_CLASS_NAME);
        Method loadTestsMethod = findLoadTestsMethod(loaderClass,
                remoteTestRunnerType);

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
        return allRefs;
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
