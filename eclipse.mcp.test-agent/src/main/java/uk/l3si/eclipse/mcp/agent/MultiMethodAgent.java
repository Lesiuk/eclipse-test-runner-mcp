package uk.l3si.eclipse.mcp.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent that registers the {@link RunMethodTransformer} to intercept
 * Eclipse's RemoteTestRunner and support running multiple specific test
 * methods in a single JVM launch.
 */
public class MultiMethodAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        inst.addTransformer(new RunMethodTransformer());
    }
}
