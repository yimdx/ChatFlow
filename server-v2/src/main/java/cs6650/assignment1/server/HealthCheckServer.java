package cs6650.assignment1.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Simple HTTP server for health check endpoint
 */
public class HealthCheckServer {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServer.class);
    private final HttpServer server;
    
    public HealthCheckServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.createContext("/health", new HealthHandler());
        this.server.setExecutor(null); // Use default executor
    }
    
    public void start() {
        server.start();
        logger.info("Health check server started on port {}", server.getAddress().getPort());
        logger.info("Health endpoint: http://localhost:{}/health", server.getAddress().getPort());
    }
    
    public void stop() {
        server.stop(0);
        logger.info("Health check server stopped");
    }
    
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = "{\"status\":\"healthy\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }
}
