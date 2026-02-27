package cs6650.assignment1.config;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitMQSetup {
    
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQSetup.class);
    
    public static final String EXCHANGE_NAME = "chat.exchange";
    public static final String ROUTING_KEY_PREFIX = "room.";
    
    @Autowired
    private RabbitMQChannelPool channelPool;
    
    @Value("${chat.room.count:20}")
    private int roomCount;
    
    @PostConstruct
    public void setupExchangeAndQueues() {
        logger.info("Setting up RabbitMQ exchange and queues...");
        
        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();
            
            // Declare topic exchange
            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);
            logger.info("Declared exchange: {}", EXCHANGE_NAME);
            
            // Create queues for each room (room.1 through room.20)
            for (int i = 1; i <= roomCount; i++) {
                String queueName = "room." + i;
                String routingKey = ROUTING_KEY_PREFIX + i;
                
                // Queue arguments for TTL and limits
                Map<String, Object> args = new HashMap<>();
                args.put("x-message-ttl", 86400000); // 24 hours in milliseconds
                args.put("x-max-length", 10000); // Maximum queue size
                
                // Declare queue
                channel.queueDeclare(queueName, true, false, false, args);
                
                // Bind queue to exchange
                channel.queueBind(queueName, EXCHANGE_NAME, routingKey);
                
                logger.info("Declared and bound queue: {} with routing key: {}", queueName, routingKey);
            }
            
            logger.info("RabbitMQ setup completed successfully");
            
        } catch (IOException | TimeoutException | InterruptedException e) {
            logger.error("Failed to setup RabbitMQ", e);
            throw new RuntimeException("Failed to setup RabbitMQ", e);
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }
    
    public static String getRoutingKey(String roomId) {
        return ROUTING_KEY_PREFIX + roomId;
    }
}
