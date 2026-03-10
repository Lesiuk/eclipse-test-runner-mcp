package uk.l3si.eclipse.mcp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
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
        server = new McpHttpServer(toolRegistry);
        try {
            server.start();
            log(IStatus.INFO, "MCP HTTP server listening on port " + McpHttpServer.PORT);
        } catch (Exception ex) {
            log(IStatus.ERROR, "MCP HTTP server failed to start: " + ex.getMessage());
            server = null;
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
