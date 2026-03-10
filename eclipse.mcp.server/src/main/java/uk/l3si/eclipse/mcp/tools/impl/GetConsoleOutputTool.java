package uk.l3si.eclipse.mcp.tools.impl;

import uk.l3si.eclipse.mcp.model.ConsoleOutputResult;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.IMcpTool;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.TextConsole;

public class GetConsoleOutputTool implements IMcpTool {

    @Override
    public String getName() {
        return "get_console_output";
    }

    @Override
    public String getDescription() {
        return "Get the console output (stdout and stderr) of the most recent launch, or a specific launch by name.";
    }

    @Override
    public InputSchema getInputSchema() {
        return InputSchema.builder()
                .property("name", PropertySchema.string("Optional: name of the launch configuration. If omitted, uses the most recent launch."))
                .build();
    }

    @Override
    public Object execute(Args args) throws Exception {
        String configName = args.getString("name");
        ILaunch launch = findLaunch(configName);

        StringBuilder stdoutBuf = new StringBuilder();
        StringBuilder stderrBuf = new StringBuilder();

        captureFromStreamMonitors(launch, stdoutBuf, stderrBuf);

        if (stdoutBuf.isEmpty() && stderrBuf.isEmpty()) {
            captureFromConsoleView(launch, stdoutBuf);
        }

        String resolvedName = launch.getLaunchConfiguration() != null
                ? launch.getLaunchConfiguration().getName()
                : "unknown";

        return ConsoleOutputResult.builder()
                .configName(resolvedName)
                .terminated(launch.isTerminated())
                .stdout(stdoutBuf.toString())
                .stderr(stderrBuf.toString())
                .build();
    }

    private ILaunch findLaunch(String configName) {
        ILaunch[] allLaunches = DebugPlugin.getDefault().getLaunchManager().getLaunches();

        for (int i = allLaunches.length - 1; i >= 0; i--) {
            ILaunch candidate = allLaunches[i];
            if (configName == null) {
                return candidate;
            }
            if (candidate.getLaunchConfiguration() != null
                    && configName.equals(candidate.getLaunchConfiguration().getName())) {
                return candidate;
            }
        }

        throw new IllegalArgumentException(configName != null
                ? "No launch found for: " + configName
                : "No launches found");
    }

    private void captureFromStreamMonitors(ILaunch launch, StringBuilder stdout, StringBuilder stderr) {
        for (IProcess proc : launch.getProcesses()) {
            IStreamsProxy proxy = proc.getStreamsProxy();
            if (proxy == null) continue;

            IStreamMonitor outMon = proxy.getOutputStreamMonitor();
            if (outMon != null) {
                stdout.append(outMon.getContents());
            }

            IStreamMonitor errMon = proxy.getErrorStreamMonitor();
            if (errMon != null) {
                stderr.append(errMon.getContents());
            }
        }
    }

    /**
     * Fallback when stream monitor buffers are empty.
     * Eclipse's Console view calls setBuffered(false) on stream monitors after
     * attaching its own listener, clearing the buffer. Read from the Console
     * document instead (same text the user sees). Runs on the UI thread.
     */
    private void captureFromConsoleView(ILaunch launch, StringBuilder output) {
        Display display = PlatformUI.getWorkbench().getDisplay();
        display.syncExec(() -> {
            try {
                var consoleMgr = ConsolePlugin.getDefault().getConsoleManager();
                for (var console : consoleMgr.getConsoles()) {
                    if (!(console instanceof org.eclipse.debug.ui.console.IConsole debugConsole)) continue;
                    if (!(console instanceof TextConsole textConsole)) continue;

                    IProcess consoleProc = debugConsole.getProcess();
                    for (IProcess launchProc : launch.getProcesses()) {
                        if (!consoleProc.equals(launchProc)) continue;

                        String content = textConsole.getDocument().get();
                        if (content != null && !content.isEmpty()) {
                            output.append(content);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Fallback may not work in all Eclipse configurations
            }
        });
    }
}
