package uk.l3si.eclipse.mcp.core.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentJarLocatorTest {

    @Test
    void agentJarNameIsCorrect() {
        assertEquals("eclipse-mcp-test-agent.jar", AgentJarLocator.AGENT_JAR_NAME);
    }

    @Test
    void bundlePathIncludesLibPrefix() {
        assertEquals("lib/eclipse-mcp-test-agent.jar", AgentJarLocator.BUNDLE_PATH);
    }
}
