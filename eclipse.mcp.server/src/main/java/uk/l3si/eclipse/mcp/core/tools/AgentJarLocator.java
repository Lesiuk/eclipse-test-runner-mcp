package uk.l3si.eclipse.mcp.core.tools;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Locates the test-agent JAR bundled inside the Eclipse plugin.
 * The JAR is shipped at {@code lib/eclipse-mcp-test-agent.jar} inside the plugin
 * and is resolved to a filesystem path at runtime.
 */
public class AgentJarLocator {

    static final String AGENT_JAR_NAME = "eclipse-mcp-test-agent.jar";
    static final String BUNDLE_PATH = "lib/" + AGENT_JAR_NAME;

    private static volatile String cachedPath;

    /**
     * Returns the absolute filesystem path to the agent JAR.
     * Resolves via the OSGi bundle entry first; falls back to extracting to a temp file.
     */
    public static String getAgentJarPath() throws IOException {
        String path = cachedPath;
        if (path != null) {
            return path;
        }
        synchronized (AgentJarLocator.class) {
            path = cachedPath;
            if (path != null) {
                return path;
            }
            path = resolve();
            cachedPath = path;
            return path;
        }
    }

    private static String resolve() throws IOException {
        Bundle bundle = Platform.getBundle("eclipse.mcp.server");
        if (bundle == null) {
            throw new IOException("Bundle 'eclipse.mcp.server' not found");
        }

        URL entry = bundle.getEntry(BUNDLE_PATH);
        if (entry == null) {
            throw new IOException("Agent JAR not found in bundle at " + BUNDLE_PATH);
        }

        try {
            URL fileUrl = FileLocator.toFileURL(entry);
            return new java.io.File(fileUrl.getPath()).getAbsolutePath();
        } catch (IOException e) {
            // Fall back: extract to temp file
            return extractToTemp(entry);
        }
    }

    private static String extractToTemp(URL entry) throws IOException {
        Path tempFile = Files.createTempFile("eclipse-mcp-agent-", ".jar");
        tempFile.toFile().deleteOnExit();
        try (InputStream in = entry.openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile.toAbsolutePath().toString();
    }

    /** Visible for testing — resets cached path. */
    static void resetCache() {
        cachedPath = null;
    }
}
