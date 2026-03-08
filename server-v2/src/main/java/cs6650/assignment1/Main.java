package cs6650.assignment1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.queue.MessagePublisher;
import cs6650.assignment1.queue.RabbitMQChannelPool;
import cs6650.assignment1.queue.RabbitMQSetup;
import cs6650.assignment1.server.ChatWebSocketServer;
import cs6650.assignment1.server.HealthCheckServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {
    
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    
    // Configuration - can be overridden by environment variables
    private static final int HEALTH_PORT = getEnvInt("HEALTH_PORT", 8080);
    private static final int WEBSOCKET_PORT = getEnvInt("WEBSOCKET_PORT", 8081);
    private static final String RABBITMQ_HOST = getEnv("RABBITMQ_HOST", "localhost");
    private static final int RABBITMQ_PORT = getEnvInt("RABBITMQ_PORT", 5672);
    private static final String RABBITMQ_USERNAME = getEnv("RABBITMQ_USERNAME", "guest");
    private static final String RABBITMQ_PASSWORD = getEnv("RABBITMQ_PASSWORD", "guest");
    private static final int RABBITMQ_POOL_SIZE = getEnvInt("RABBITMQ_POOL_SIZE", 20);
    private static final int ROOM_COUNT = getEnvInt("ROOM_COUNT", 20);
    private static final String SERVER_ID = getEnv("SERVER_ID", "server-1");
    private static final int BROADCAST_PORT = getEnvInt("BROADCAST_PORT", 8082);
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("ChatFlow Server v2 (with RabbitMQ)");
        logger.info("========================================");
        logger.info("Server ID: {}", SERVER_ID);
        logger.info("Health endpoint port: {}", HEALTH_PORT);
        logger.info("WebSocket endpoint port: {}", WEBSOCKET_PORT);
        logger.info("RabbitMQ: {}:{}", RABBITMQ_HOST, RABBITMQ_PORT);
        logger.info("========================================");
        
        RabbitMQChannelPool channelPool = null;
        HealthCheckServer healthServer = null;
        ChatWebSocketServer wsServer = null;
        HttpServer broadcastServer = null;
        
        try {
            // Initialize RabbitMQ connection pool
            logger.info("Initializing RabbitMQ connection...");
            channelPool = new RabbitMQChannelPool(
                RABBITMQ_HOST, 
                RABBITMQ_PORT, 
                RABBITMQ_USERNAME, 
                RABBITMQ_PASSWORD, 
                RABBITMQ_POOL_SIZE
            );
            channelPool.init();
            
            // Setup RabbitMQ exchange and queues
            RabbitMQSetup rabbitMQSetup = new RabbitMQSetup(channelPool, ROOM_COUNT);
            rabbitMQSetup.setupExchangeAndQueues();
            
            // Create ObjectMapper for JSON serialization
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            
            // Create message publisher
            MessagePublisher messagePublisher = new MessagePublisher(channelPool, objectMapper);
            
            // Start health check HTTP server
            logger.info("Starting health check server...");
            healthServer = new HealthCheckServer(HEALTH_PORT);
            healthServer.start();
            
            // Create and start the WebSocket server
            logger.info("Starting WebSocket server...");
            wsServer = new ChatWebSocketServer(WEBSOCKET_PORT, messagePublisher, SERVER_ID);
            wsServer.start();
            
            // Start HTTP broadcast server to receive messages from consumer
            logger.info("Starting HTTP broadcast server on port {}...", BROADCAST_PORT);
            broadcastServer = HttpServer.create(new InetSocketAddress(BROADCAST_PORT), 0);
            final ChatWebSocketServer finalWsServerForBroadcast = wsServer;
            
            broadcastServer.createContext("/broadcast", exchange -> {
                if ("POST".equals(exchange.getRequestMethod())) {
                    try {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        QueueMessage message = objectMapper.readValue(body, QueueMessage.class);
                        
                        // Broadcast to all WebSocket clients in this room
                        finalWsServerForBroadcast.broadcastToRoom(message);
                        
                        // Send 200 OK response
                        String response = "{\"status\":\"ok\"}";
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length());
                        exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
                        exchange.getResponseBody().close();
                        
                    } catch (Exception e) {
                        logger.error("Error processing broadcast request", e);
                        exchange.sendResponseHeaders(500, 0);
                        exchange.getResponseBody().close();
                    }
                } else {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.getResponseBody().close();
                }
            });
            
            broadcastServer.setExecutor(null);
            broadcastServer.start();
            logger.info("HTTP broadcast server started on port {}", BROADCAST_PORT);
            
            // Create final references for shutdown hook
            final RabbitMQChannelPool finalChannelPool = channelPool;
            final HealthCheckServer finalHealthServer = healthServer;
            final ChatWebSocketServer finalWsServer = wsServer;
            final HttpServer finalBroadcastServer = broadcastServer;
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down servers...");
                try {
                    if (finalBroadcastServer != null) {
                        finalBroadcastServer.stop(0);
                    }
                    if (finalWsServer != null) {
                        finalWsServer.stop(1000);
                    }
                    if (finalHealthServer != null) {
                        finalHealthServer.stop();
                    }
                    if (finalChannelPool != null) {
                        finalChannelPool.cleanup();
                    }
                    logger.info("Servers stopped successfully");
                } catch (Exception e) {
                    logger.error("Error stopping servers", e);
                }
            }));
            
            logger.info("========================================");
            logger.info("Servers are running!");
            logger.info("REST Health endpoint: http://localhost:{}/health", HEALTH_PORT);
            logger.info("WebSocket endpoint: ws://localhost:{}/chat/{{roomId}}", WEBSOCKET_PORT);
            logger.info("Valid room IDs: 1-20");
            logger.info("Press Ctrl+C to stop");
            logger.info("Internal Broadcast HTTP endpoint: http://localhost:{}/broadcast", BROADCAST_PORT);
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            
            // Cleanup on failure
            if (wsServer != null) {
                try {
                    wsServer.stop(1000);
                } catch (Exception ex) {
                    logger.error("Error stopping WebSocket server", ex);
                }
            }
            if (healthServer != null) {
                healthServer.stop();
            }
            if (channelPool != null) {
                channelPool.cleanup();
            }
            
            System.exit(1);
        }
    }
    
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
    
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }
}