package cs6650.assignment1;

import cs6650.assignment1.server.ChatWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int DEFAULT_PORT = 8080;
    
    public static void main(String[] args) {
        // Get port from arguments or use default
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid port number '{}', using default port {}", args[0], DEFAULT_PORT);
            }
        }
        
        logger.info("========================================");
        logger.info("ChatFlow WebSocket Server");
        logger.info("========================================");
        logger.info("Port: {}", port);
        logger.info("========================================");
        
        // Create and start the WebSocket server
        ChatWebSocketServer server = new ChatWebSocketServer(port);
        server.start();
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            try {
                server.stop(1000);
                logger.info("Server stopped successfully");
            } catch (InterruptedException e) {
                logger.error("Error stopping server", e);
                Thread.currentThread().interrupt();
            }
        }));
        
        logger.info("========================================");
        logger.info("Server is running!");
        logger.info("WebSocket endpoint: ws://localhost:{}/chat/{{roomId}}", port);
        logger.info("Press Ctrl+C to stop");
        logger.info("========================================");
    }
}