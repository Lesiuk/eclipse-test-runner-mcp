package uk.l3si.eclipse.mcp.core.tools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiMethodLaunchTest {

    @Test
    void buildMultiMethodVmArgs_addsAgentAndProperty() {
        String result = TestLaunchHelper.buildMultiMethodVmArgs(
                "/path/to/agent.jar", List.of("testA", "testB"), null);
        assertTrue(result.contains("-javaagent:/path/to/agent.jar"));
        assertTrue(result.contains("-Declipse.mcp.test.methods=testA,testB"));
    }

    @Test
    void buildMultiMethodVmArgs_preservesExistingArgs() {
        String result = TestLaunchHelper.buildMultiMethodVmArgs(
                "/path/to/agent.jar", List.of("testA"), "-Xmx512m -Dfoo=bar");
        assertTrue(result.startsWith("-Xmx512m -Dfoo=bar "));
        assertTrue(result.contains("-javaagent:/path/to/agent.jar"));
        assertTrue(result.contains("-Declipse.mcp.test.methods=testA"));
    }

    @Test
    void buildMultiMethodVmArgs_handlesNullExisting() {
        String result = TestLaunchHelper.buildMultiMethodVmArgs(
                "/agent.jar", List.of("testX", "testY", "testZ"), null);
        assertTrue(result.startsWith("-javaagent:/agent.jar"));
        assertTrue(result.contains("-Declipse.mcp.test.methods=testX,testY,testZ"));
    }

    @Test
    void isMultiMethod_singleMethodReturnsFalse() {
        assertFalse(TestLaunchHelper.isMultiMethod(List.of("testOne")));
    }

    @Test
    void isMultiMethod_multipleMethodsReturnsTrue() {
        assertTrue(TestLaunchHelper.isMultiMethod(List.of("testA", "testB")));
    }

    @Test
    void isMultiMethod_nullReturnsFalse() {
        assertFalse(TestLaunchHelper.isMultiMethod(null));
    }
}
