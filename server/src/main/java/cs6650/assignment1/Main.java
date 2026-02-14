package cs6650.assignment1;

import cs6650.assignment1.server.ChatWebSocketServer;
import cs6650.assignment1.server.HealthCheckServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int HEALTH_PORT = 8080;
    private static final int WEBSOCKET_PORT = 8081;
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("ChatFlow Server");
        logger.info("========================================");
        logger.info("Health endpoint port: {}", HEALTH_PORT);
        logger.info("WebSocket endpoint port: {}", WEBSOCKET_PORT);
        logger.info("========================================");
        
        try {
            // Start health check HTTP server
            HealthCheckServer healthServer = new HealthCheckServer(HEALTH_PORT);
            healthServer.start();
            
            // Create and start the WebSocket server
            ChatWebSocketServer wsServer = new ChatWebSocketServer(WEBSOCKET_PORT);
            wsServer.start();
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down servers...");
                try {
                    healthServer.stop();
                    wsServer.stop(1000);
                    logger.info("Servers stopped successfully");
                } catch (InterruptedException e) {
                    logger.error("Error stopping servers", e);
                    Thread.currentThread().interrupt();
                }
            }));
            
            logger.info("========================================");
            logger.info("Servers are running!");
            logger.info("REST Health endpoint: http://localhost:{}/health", HEALTH_PORT);
            logger.info("WebSocket endpoint: ws://localhost:{}/chat/{{roomId}}", WEBSOCKET_PORT);
            logger.info("Valid room IDs: 1-20");
            logger.info("Press Ctrl+C to stop");
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }
}