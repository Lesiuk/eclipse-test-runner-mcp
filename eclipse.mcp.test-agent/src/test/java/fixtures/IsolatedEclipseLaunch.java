package fixtures;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

/** Runs the actual Eclipse entry point in a forked JVM; Eclipse calls System.exit. */
public class IsolatedEclipseLaunch {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        if (Boolean.parseBoolean(args[0])) {
            URL[] urls = Arrays.stream(args[1].split(File.pathSeparator))
                    .map(p -> {
                        try { return Path.of(p).toUri().toURL(); }
                        catch (Exception e) { throw new IllegalArgumentException(e); }
                    }).toArray(URL[]::new);
            loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.startsWith("uk.l3si.eclipse.mcp.agent.")) {
                        throw new ClassNotFoundException(name);
                    }
                    return super.loadClass(name, resolve);
                }
            };
            try {
                loader.loadClass("uk.l3si.eclipse.mcp.agent.MultiMethodRunner");
                throw new AssertionError("Fixture must isolate the runner from the agent");
            } catch (ClassNotFoundException expected) {
                // The injected call must bridge this boundary explicitly.
            }
        }
        Thread.currentThread().setContextClassLoader(loader);
        Class<?> runner = loader.loadClass("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner");
        runner.getMethod("main", String[].class).invoke(null, (Object) new String[]{
                "-junitconsole", "-port", "0", "-classnames", args[2],
                "-testloaderclass", args[3]});
    }
}
