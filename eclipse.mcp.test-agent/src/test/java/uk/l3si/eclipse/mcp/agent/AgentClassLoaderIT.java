package uk.l3si.eclipse.mcp.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AgentClassLoaderIT {
    @TempDir Path temp;

    @ParameterizedTest
    @CsvSource({"true,4,true", "true,5,true", "false,4,true", "false,5,true",
            "true,5,false"})
    void packagedAgentRunsSelectedMethods(boolean isolated, int junit, boolean multi) throws Exception {
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-javaagent:" + System.getProperty("agentJar")));
        if (multi) command.add("-Declipse.mcp.test.methods=alpha,beta");
        command.addAll(List.of("-cp", isolated ? "target/test-classes" : cp,
                "fixtures.IsolatedEclipseLaunch", Boolean.toString(isolated), cp,
                "fixtures.SelectedMethods",
                "org.eclipse.jdt.internal.junit" + junit + ".runner.JUnit" + junit + "TestLoader"));
        Path output = temp.resolve("console.txt");
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        process.getOutputStream().close(); // Console-mode reader must see EOF during shutdown.
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Eclipse runner timed out");
            String console = Files.readString(output);
            assertEquals(0, process.exitValue(), console);
            assertTrue(console.contains("EXECUTED_ALPHA"), console);
            assertTrue(console.contains("EXECUTED_BETA"), console);
            assertEquals(!multi, console.contains("EXECUTED_GAMMA"), console);
            assertTrue(console.contains(multi ? "%TESTC  2 v2" : "%TESTC  3 v2"), console);
            assertTrue(console.contains("%RUNTIME"), console);
            assertFalse(console.contains("Exception"), console);
            assertFalse(console.contains("NoClassDefFoundError"), console);
        } finally {
            process.destroyForcibly();
        }
    }
}
