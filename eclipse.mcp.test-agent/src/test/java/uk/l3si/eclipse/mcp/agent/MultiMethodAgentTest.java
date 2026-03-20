package uk.l3si.eclipse.mcp.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

import org.junit.jupiter.api.Test;

class MultiMethodAgentTest {

    @Test
    void premainRegistersTransformer() {
        Instrumentation inst = mock(Instrumentation.class);
        MultiMethodAgent.premain(null, inst);
        verify(inst).addTransformer(any(RunMethodTransformer.class));
    }
}
