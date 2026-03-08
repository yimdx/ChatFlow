package cs6650.assignment1.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.server.ChatWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * BroadcastConsumer listens for broadcast messages published by the consumer service.
 * Each server instance gets its own exclusive, auto-delete queue bound to the
 * fanout exchange, ensuring EVERY server receives EVERY broadcast message.
 */
public class BroadcastConsumer implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(BroadcastConsumer.class);
    private static final String BROADCAST_EXCHANGE = "broadcast.fanout";
    
    private final RabbitMQChannelPool channelPool;
    private final ChatWebSocketServer webSocketServer;
    private final ObjectMapper objectMapper;
    private final String serverId;
    private volatile boolean running = true;
    private Channel channel;
    
    public BroadcastConsumer(RabbitMQChannelPool channelPool, 
                            ChatWebSocketServer webSocketServer,
                            ObjectMapper objectMapper,
                            String serverId) {
        this.channelPool = channelPool;
        this.webSocketServer = webSocketServer;
        this.objectMapper = objectMapper;
        this.serverId = serverId;
    }
    
    @Override
    public void run() {
        logger.info("BroadcastConsumer starting for server {}...", serverId);
        
        try {
            channel = channelPool.borrowChannel();
            
            // Declare the fanout exchange (must match what BroadcastPublisher uses)
            channel.exchangeDeclare(BROADCAST_EXCHANGE, "fanout", true);
            
            // Each server gets its own exclusive, auto-delete queue.
            // "" name lets RabbitMQ generate a unique name per server instance.
            // exclusive=true: only this connection can use it.
            // autoDelete=true: deleted when this server disconnects.
            String queueName = channel.queueDeclare("", false, true, true, null).getQueue();
            
            // Bind this server's queue to the fanout exchange
            channel.queueBind(queueName, BROADCAST_EXCHANGE, "");
            
            logger.info("Server {} bound to exchange {} via queue {}", serverId, BROADCAST_EXCHANGE, queueName);
            
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);
                    logger.debug("Received broadcast message: {}", messageJson);
                    
                    // Parse the queue message
                    QueueMessage queueMessage = objectMapper.readValue(messageJson, QueueMessage.class);
                    
                    // Broadcast to all clients in the room via WebSocket
                    webSocketServer.broadcastToRoom(queueMessage);
                    
                    // Acknowledge message
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    
                } catch (Exception e) {
                    logger.error("Error processing broadcast message: {}", e.getMessage(), e);
                    
                    // Negative acknowledge, don't requeue (fanout messages are not retriable)
                    try {
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                    } catch (IOException ioException) {
                        logger.error("Error sending NACK", ioException);
                    }
                }
            };
            
            // Auto-ack=false for manual acknowledgment
            channel.basicConsume(queueName, false, deliverCallback, 
                               consumerTag -> logger.info("Consumer cancelled: {}", consumerTag));
            
            logger.info("BroadcastConsumer for server {} listening on queue {}", serverId, queueName);
            
            // Keep thread alive
            while (running) {
                Thread.sleep(1000);
            }
            
        } catch (IOException | InterruptedException e) {
            logger.error("BroadcastConsumer encountered error: {}", e.getMessage(), e);
        } finally {
            cleanup();
        }
        
        logger.info("BroadcastConsumer stopped");
    }
    
    public void stop() {
        running = false;
    }
    
    private void cleanup() {
        if (channel != null && channel.isOpen()) {
            try {
                channelPool.returnChannel(channel);
            } catch (Exception e) {
                logger.error("Error returning channel", e);
            }
        }
    }
}
