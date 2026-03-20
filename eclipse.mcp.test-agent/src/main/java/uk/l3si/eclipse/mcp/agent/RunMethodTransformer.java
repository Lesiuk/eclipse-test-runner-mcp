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

            // MultiMethodRunner.execute(this);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "uk/l3si/eclipse/mcp/agent/MultiMethodRunner",
                    "execute", "(Ljava/lang/Object;)V", false);

            // return;
            mv.visitInsn(Opcodes.RETURN);

            mv.visitLabel(originalStart);
        }
    }
}
