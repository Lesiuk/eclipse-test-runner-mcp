package uk.l3si.eclipse.mcp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "eclipse.mcp.server";

    private static Activator plugin;
    private McpHttpServer server;
    private ToolRegistry toolRegistry;

    public static Activator getInstance() {
        return plugin;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    @Override
    public void start(BundleContext ctx) throws Exception {
        super.start(ctx);
        plugin = this;
        toolRegistry = new ToolRegistry();
    }

    @Override
    public void stop(BundleContext ctx) throws Exception {
        shutdownServer();
        plugin = null;
        super.stop(ctx);
    }

    public void initServer() {
        if (server != null) {
            return;
        }
        suppressDebugActivation();
        server = new McpHttpServer(toolRegistry);
        try {
            server.start();
            log(IStatus.INFO, "MCP HTTP server listening on port " + McpHttpServer.PORT);
        } catch (Exception ex) {
            log(IStatus.ERROR, "MCP HTTP server failed to start: " + ex.getMessage());
            server = null;
        }
    }

    /**
     * Prevent Eclipse from stealing window focus when a breakpoint is hit.
     */
    private void suppressDebugActivation() {
        try {
            IPreferenceStore prefs = org.eclipse.debug.ui.DebugUITools.getPreferenceStore();
            prefs.setValue(IDebugUIConstants.PREF_ACTIVATE_WORKBENCH, false);
        } catch (Exception e) {
            log(IStatus.WARNING, "Could not suppress debug activation: " + e.getMessage());
        }
    }

    public void shutdownServer() {
        if (server == null) {
            return;
        }
        server.stop();
        server = null;
    }

    private void log(int severity, String msg) {
        getLog().log(new Status(severity, PLUGIN_ID, msg));
    }
}
