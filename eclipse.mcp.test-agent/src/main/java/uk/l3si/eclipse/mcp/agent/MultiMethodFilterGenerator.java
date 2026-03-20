package uk.l3si.eclipse.mcp.agent;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates a JUnit 4 {@code Filter} subclass at runtime using ASM.
 * The generated filter accepts only test methods whose names are in the
 * provided set. Suite nodes (where {@code getMethodName()} returns null)
 * are always accepted so the class container appears in the JUnit view.
 */
final class MultiMethodFilterGenerator {

    private static final String GENERATED_NAME =
            "uk/l3si/eclipse/mcp/agent/generated/MultiMethodFilter";
    private static final String DESCRIPTION_INTERNAL =
            "org/junit/runner/Description";

    private MultiMethodFilterGenerator() {}

    /**
     * Creates a new Filter instance that accepts only the given method names.
     *
     * @param parentClassLoader classloader that can see both {@code Filter} and JUnit types
     * @param filterSuperClass  the {@code org.junit.runner.manipulation.Filter} class
     * @param methods           method names to accept
     * @return a Filter instance
     */
    static Object create(ClassLoader parentClassLoader, Class<?> filterSuperClass,
            String[] methods) throws Exception {
        byte[] bytecode = generateFilterBytecode(filterSuperClass);
        Class<?> filterClass = new ReflectionUtils.ByteArrayClassLoader(parentClassLoader)
                .defineClass(GENERATED_NAME.replace('/', '.'), bytecode);
        Set<String> methodSet = new HashSet<>(Arrays.asList(methods));
        Constructor<?> ctor = filterClass.getConstructor(Set.class);
        return ctor.newInstance(methodSet);
    }

    private static byte[] generateFilterBytecode(Class<?> filterSuperClass) {
        String superInternal = Type.getInternalName(filterSuperClass);
        String setDesc = Type.getDescriptor(Set.class);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, GENERATED_NAME, null, superInternal, null);

        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "methodNames", setDesc, null, null).visitEnd();

        generateConstructor(cw, superInternal, setDesc);
        generateShouldRun(cw, setDesc);
        generateDescribe(cw);

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Constructor that takes a {@code Set<String>} and stores it. */
    private static void generateConstructor(ClassWriter cw, String superInternal, String setDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(" + setDesc + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, superInternal, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, GENERATED_NAME, "methodNames", setDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * {@code shouldRun(Description d)}: returns true if {@code d.getMethodName()} is null
     * (suite node) or if the method name is in the set.
     */
    private static void generateShouldRun(ClassWriter cw, String setDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "shouldRun",
                "(L" + DESCRIPTION_INTERNAL + ";)Z", null, null);
        mv.visitCode();

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DESCRIPTION_INTERNAL,
                "getMethodName", "()Ljava/lang/String;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);

        Label notNull = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitLabel(notNull);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, GENERATED_NAME, "methodNames", setDesc);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(Set.class),
                "contains", "(Ljava/lang/Object;)Z", true);
        mv.visitInsn(Opcodes.IRETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** {@code describe()}: returns {@code "MultiMethodFilter"}. */
    private static void generateDescribe(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "describe",
                "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn("MultiMethodFilter");
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
