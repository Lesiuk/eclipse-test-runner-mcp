package uk.l3si.eclipse.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import uk.l3si.eclipse.mcp.tools.ToolRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class McpHttpServer {

    static final int PORT = 5188;
    private static final String BIND_ADDRESS = "127.0.0.1";
    private static final String ENDPOINT = "/mcp";

    private final McpProtocolHandler protocolHandler;
    private HttpServer httpServer;

    public McpHttpServer(ToolRegistry registry) {
        this.protocolHandler = new McpProtocolHandler(registry);
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(BIND_ADDRESS, PORT), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool(this::createDaemonThread));
        httpServer.createContext(ENDPOINT, this::handleRequest);
        httpServer.start();
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    private Thread createDaemonThread(Runnable task) {
        Thread thread = new Thread(task, "MCP-Worker");
        thread.setDaemon(true);
        return thread;
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String requestBody;
            try (var input = exchange.getRequestBody()) {
                requestBody = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }

            String responseJson = protocolHandler.handleMessage(requestBody);

            if (responseJson == null) {
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(responseBytes);
            }
        } finally {
            exchange.close();
        }
    }
}
