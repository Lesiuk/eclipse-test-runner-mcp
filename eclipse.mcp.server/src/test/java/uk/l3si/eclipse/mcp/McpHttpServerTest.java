package uk.l3si.eclipse.mcp;

import org.junit.jupiter.api.Test;
import uk.l3si.eclipse.mcp.tools.Args;
import uk.l3si.eclipse.mcp.tools.InputSchema;
import uk.l3si.eclipse.mcp.tools.McpTool;
import uk.l3si.eclipse.mcp.tools.ProgressReporter;
import uk.l3si.eclipse.mcp.tools.PropertySchema;
import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class McpHttpServerTest {

    @Test
    void packageProgressIsDeliveredLiveOverHttpBeforeFinalResponse() throws Exception {
        CountDownLatch firstProgressReported = new CountDownLatch(1);
        CountDownLatch releaseTool = new CountDownLatch(1);
        ToolRegistry registry = new ToolRegistry();
        registry.addTool(new McpTool() {
            @Override
            public String getName() {
                return "run_test";
            }

            @Override
            public String getDescription() {
                return "package test stream fixture";
            }

            @Override
            public InputSchema getInputSchema() {
                return InputSchema.builder()
                        .property("config", PropertySchema.string("configuration"))
                        .property("package", PropertySchema.string("test package"))
                        .property("project", PropertySchema.string("test project"))
                        .build();
            }

            @Override
            public Object execute(Args args, ProgressReporter progress) throws Exception {
                String packageName = args.getString("package");
                progress.report("Refreshing " + packageName + "...");
                firstProgressReported.countDown();
                if (!releaseTool.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test tool was not released");
                }
                progress.report("PASSED: UnitTest.testFast (0.1s)");
                return Map.of("package", packageName, "success", true);
            }
        });

        McpHttpServer server = new McpHttpServer(registry, 0);
        ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
        try {
            server.start();
            String json = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
                    + "\"params\":{\"name\":\"run_test\",\"arguments\":{"
                    + "\"config\":\"Unit tests\",\"package\":\"com.example.unit\","
                    + "\"project\":\"unit-project\"},"
                    + "\"_meta\":{\"progressToken\":\"pkg-live\"}}}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/mcp"))
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<InputStream> response = HttpClient.newHttpClient()
                    .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .get(5, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("")
                    .startsWith("text/event-stream"));
            assertTrue(firstProgressReported.await(5, TimeUnit.SECONDS));

            try (InputStream input = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                Future<String> firstProgressEvent = readerExecutor.submit(() -> {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ") && line.contains("notifications/progress")) {
                            return line;
                        }
                    }
                    return null;
                });

                String firstEvent = firstProgressEvent.get(2, TimeUnit.SECONDS);
                assertNotNull(firstEvent, "first package progress event should arrive while the tool is blocked");
                assertTrue(firstEvent.contains("pkg-live"));
                assertTrue(firstEvent.contains("com.example.unit"));

                releaseTool.countDown();
                StringBuilder remaining = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    remaining.append(line).append('\n');
                }
                assertTrue(remaining.toString().contains("PASSED: UnitTest.testFast (0.1s)"));
                assertTrue(remaining.toString().contains("\"id\":7"));
                assertTrue(remaining.toString().contains("com.example.unit"));
            }
        } finally {
            releaseTool.countDown();
            server.stop();
            readerExecutor.shutdownNow();
        }
    }
}
