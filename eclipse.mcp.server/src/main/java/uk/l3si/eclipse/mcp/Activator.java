package uk.l3si.eclipse.mcp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import java.util.LinkedHashSet;
import java.util.Set;

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
        if (toolRegistry != null) {
            toolRegistry.getDebugContext().unregister();
        }
        plugin = null;
        super.stop(ctx);
    }

    void doEarlyStartup() {
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
        applyDisabledTools();
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

    public void applyDisabledTools() {
        String disabled = getPreferenceStore().getString(McpToolPreferencePage.PREF_DISABLED_TOOLS);
        if (disabled != null && !disabled.isBlank()) {
            Set<String> disabledSet = new LinkedHashSet<>();
            for (String name : disabled.split(",")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    disabledSet.add(trimmed);
                }
            }
            toolRegistry.setDisabledTools(disabledSet);
        } else {
            toolRegistry.setDisabledTools(toolRegistry.getDefaultDisabledTools());
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
