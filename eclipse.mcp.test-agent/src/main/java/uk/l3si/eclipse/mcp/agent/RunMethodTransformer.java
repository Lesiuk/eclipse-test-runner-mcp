package uk.l3si.eclipse.mcp.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM-based transformer that injects a preamble into
 * {@code RemoteTestRunner.run()} to support running multiple specific test
 * methods in a single JVM launch.
 */
public class RunMethodTransformer implements ClassFileTransformer {

    static final String PROPERTY_NAME = "eclipse.mcp.test.methods";

    private static final String TARGET_CLASS =
            "org/eclipse/jdt/internal/junit/runner/RemoteTestRunner";

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        if (!TARGET_CLASS.equals(className)) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader,
                    ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name,
                                                 String descriptor,
                                                 String signature,
                                                 String[] exceptions) {
                    MethodVisitor mv = super.visitMethod(access, name,
                            descriptor, signature, exceptions);
                    if ("run".equals(name) && "()V".equals(descriptor)) {
                        return new InjectPreambleVisitor(mv);
                    }
                    return mv;
                }
            };
            reader.accept(visitor, 0);
            return writer.toByteArray();
        } catch (Exception e) {
            System.err.println("[eclipse-mcp-agent] Error transforming "
                    + className + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Method visitor that injects a preamble at the start of the method to
     * check for the system property and delegate to MultiMethodRunner.
     */
    private static class InjectPreambleVisitor extends MethodVisitor {

        InjectPreambleVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();

            Label originalStart = new Label();

            // String val = System.getProperty("eclipse.mcp.test.methods");
            mv.visitLdcInsn(PROPERTY_NAME);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System",
                    "getProperty",
                    "(Ljava/lang/String;)Ljava/lang/String;", false);

            // if (val == null) goto originalStart;
            mv.visitJumpInsn(Opcodes.IFNULL, originalStart);

            // A -javaagent helper is loaded by the system loader. Eclipse/PDE may
            // define the runner in a loader that cannot see that helper. Bridge
            // using only java.base types, without a symbolic helper reference.
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/ClassLoader",
                    "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false);
            mv.visitLdcInsn("uk.l3si.eclipse.mcp.agent.MultiMethodRunner");
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/ClassLoader",
                    "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
            mv.visitLdcInsn("execute");
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            mv.visitInsn(Opcodes.DUP);
            mv.visitInsn(Opcodes.ICONST_0);
            // Do not use ldc Object.class here.  Class literals are not valid
            // ldc constants in pre-Java-5 class files, and older Eclipse
            // runners still use those class-file versions.
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
            mv.visitInsn(Opcodes.DUP);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
            mv.visitInsn(Opcodes.AASTORE);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
            mv.visitInsn(Opcodes.ACONST_NULL); // static receiver
            mv.visitInsn(Opcodes.ICONST_1);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            mv.visitInsn(Opcodes.DUP);
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitInsn(Opcodes.AASTORE);

            Label invokeStart = new Label();
            Label invokeEnd = new Label();
            Label failed = new Label();
            mv.visitTryCatchBlock(invokeStart, invokeEnd, failed,
                    "java/lang/reflect/InvocationTargetException");
            mv.visitLabel(invokeStart);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
            mv.visitLabel(invokeEnd);
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);

            // Preserve the helper's original failure in the Eclipse console.
            mv.visitLabel(failed);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/InvocationTargetException",
                    "getCause", "()Ljava/lang/Throwable;", false);
            mv.visitInsn(Opcodes.ATHROW);

            mv.visitLabel(originalStart);
        }
    }
}
