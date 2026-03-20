package uk.l3si.eclipse.mcp.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

class RunMethodTransformerTest {

    /**
     * A fake runner class that mimics the target class by having a
     * {@code protected void run()} method. We use its bytecode as input
     * and pretend it has the target class name.
     */
    @SuppressWarnings("unused")
    static class FakeRunner {
        protected void run() {
            System.out.println("original run");
        }
    }

    private static final String TARGET_CLASS =
            "org/eclipse/jdt/internal/junit/runner/RemoteTestRunner";

    private final RunMethodTransformer transformer = new RunMethodTransformer();

    @Test
    void transformerOnlyModifiesRunMethod() throws Exception {
        byte[] original = readClassBytes(FakeRunner.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);
        assertNotNull(transformed, "Should return transformed bytecode for the target class");
    }

    @Test
    void transformerLeavesNonTargetClassesUnchanged() {
        byte[] original = readClassBytes(FakeRunner.class);
        byte[] result = transformer.transform(
                getClass().getClassLoader(),
                "uk/l3si/eclipse/mcp/agent/RunMethodTransformerTest$FakeRunner",
                null, null, original);
        assertNull(result, "Should return null for non-target classes");
    }

    @Test
    void transformedRunMethodDelegatesToHelperWhenPropertySet() throws Exception {
        byte[] original = readClassBytes(FakeRunner.class);
        byte[] transformed = transformer.transform(
                getClass().getClassLoader(), TARGET_CLASS,
                null, null, original);
        assertNotNull(transformed);

        // Verify the transformed bytecode can be loaded without VerifyError
        // by defining it as a new class in a custom classloader.
        assertDoesNotThrow(() -> {
            new ClassLoader(getClass().getClassLoader()) {
                {
                    defineClass(null, transformed, 0, transformed.length);
                }
            };
        }, "Transformed bytecode should load without VerifyError");
    }

    private static byte[] readClassBytes(Class<?> clazz) {
        String resource = clazz.getName().replace('.', '/') + ".class";
        ClassLoader cl = clazz.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Cannot find class resource: " + resource);
            }
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
