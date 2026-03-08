package eclipse.mcp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "eclipse.mcp.server";

    private static Activator plugin;
    private McpHttpServer server;

    public static Activator getInstance() {
        return plugin;
    }

    @Override
    public void start(BundleContext ctx) throws Exception {
        super.start(ctx);
        plugin = this;
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
        server = new McpHttpServer();
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
