package uk.l3si.eclipse.mcp.agent;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class RunMethodTransformerTest {

    /** Fake class with a protected void run() method, mimicking RemoteTestRunner */
    @SuppressWarnings("unused")
    static class FakeRunner {
        public boolean originalRunCalled = false;
        protected void run() { originalRunCalled = true; }
        public void otherMethod() {}
        private int anotherMethod(String arg) { return 42; }
    }

    /** Class without a run() method */
    static class NoRunMethod {
        public void doSomething() {}
    }

    private static final String TARGET_CLASS =
            "org/eclipse/jdt/internal/junit/runner/RemoteTestRunner";

    private final RunMethodTransformer transformer = new RunMethodTransformer();

    // -- Target class detection -----------------------------------------------

    @Test
    void transformsTargetClass() {
        byte[] original = readClassBytes(FakeRunner.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);
        assertNotNull(transformed, "Should return transformed bytecode for target class");
    }

    @Test
    void returnsNullForNonTargetClass() {
        byte[] original = readClassBytes(FakeRunner.class);
        assertNull(transformer.transform(null, "com/example/SomeOtherClass", null, null, original));
    }

    @Test
    void returnsNullForNullClassName() {
        byte[] original = readClassBytes(FakeRunner.class);
        assertNull(transformer.transform(null, null, null, null, original));
    }

    // -- Bytecode validity ----------------------------------------------------

    @Test
    void transformedBytecodeLoadsWithoutVerifyError() {
        byte[] original = readClassBytes(FakeRunner.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);
        assertNotNull(transformed);

        assertDoesNotThrow(() -> defineClass(transformed),
                "Transformed bytecode should load without VerifyError");
    }

    @Test
    void transformedBytecodeIsDifferentFromOriginal() {
        byte[] original = readClassBytes(FakeRunner.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);
        assertNotNull(transformed);
        // Preamble injection adds bytecode, so transformed should be larger
        assertTrue(transformed.length > original.length,
                "Transformed bytecode should be larger due to injected preamble");
    }

    // -- Error handling -------------------------------------------------------

    @Test
    void returnsNullForInvalidBytecode() {
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03};
        byte[] result = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, garbage);
        assertNull(result, "Should return null (fallback) for invalid bytecode");
    }

    @Test
    void returnsNullForEmptyBytecode() {
        byte[] result = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, new byte[0]);
        assertNull(result, "Should return null for empty bytecode");
    }

    // -- Class without run() method -------------------------------------------

    @Test
    void classWithoutRunMethodStillTransforms() {
        byte[] original = readClassBytes(NoRunMethod.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);
        // Transformation succeeds but no preamble is injected (no run() method)
        assertNotNull(transformed);
        assertDoesNotThrow(() -> defineClass(transformed));
    }

    // -- Preamble behavior verification ---------------------------------------

    @Test
    void originalRunExecutesWhenPropertyNotSet() throws Exception {
        System.clearProperty(RunMethodTransformer.PROPERTY_NAME);

        byte[] original = readClassBytes(FakeRunner.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);

        Class<?> clazz = defineClass(transformed);
        var ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object instance = ctor.newInstance();

        Method runMethod = clazz.getDeclaredMethod("run");
        runMethod.setAccessible(true);
        runMethod.invoke(instance);

        // Original run should have executed (sets originalRunCalled = true)
        java.lang.reflect.Field field = clazz.getDeclaredField("originalRunCalled");
        field.setAccessible(true);
        assertTrue((boolean) field.get(instance),
                "Original run() body should execute when property is not set");
    }

    @Test
    void preambleCallsMultiMethodRunnerWhenPropertySet() throws Exception {
        System.setProperty(RunMethodTransformer.PROPERTY_NAME, "testA,testB");
        try {
            byte[] original = readClassBytes(FakeRunner.class);
            byte[] transformed = transformer.transform(
                    getClass().getClassLoader(), TARGET_CLASS,
                    null, null, original);

            Class<?> clazz = defineClass(transformed);
            var ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            Method runMethod = clazz.getDeclaredMethod("run");
            runMethod.setAccessible(true);

            // MultiMethodRunner.execute() will throw because FakeRunner
            // is not a real RemoteTestRunner — but the fact that we get
            // an exception from execute() proves the preamble delegated
            Exception thrown = assertThrows(Exception.class,
                    () -> runMethod.invoke(instance));

            // The root cause should be from execute() trying to use the fake runner
            Throwable cause = thrown.getCause();
            assertNotNull(cause, "Should have a cause from MultiMethodRunner.execute()");

            // Original run should NOT have executed
            java.lang.reflect.Field field = clazz.getDeclaredField("originalRunCalled");
            field.setAccessible(true);
            assertFalse((boolean) field.get(instance),
                    "Original run() body should NOT execute when property is set");
        } finally {
            System.clearProperty(RunMethodTransformer.PROPERTY_NAME);
        }
    }

    // -- Helpers --------------------------------------------------------------

    private static byte[] readClassBytes(Class<?> clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        ClassLoader cl = clazz.getClassLoader();
        if (cl == null) cl = ClassLoader.getSystemClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Cannot find: " + resource);
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> defineClass(byte[] bytecode) {
        return new ClassLoader(RunMethodTransformerTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass(null, bytecode, 0, bytecode.length);
            }
        }.define();
    }
}
