package cs6650.assignment1.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import cs6650.assignment1.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * BroadcastPublisher publishes processed messages to a fanout exchange.
 * Every server instance has its own exclusive queue bound to this exchange,
 * so ALL servers receive every broadcast message and deliver it to their clients.
 */
public class BroadcastPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(BroadcastPublisher.class);
    public static final String BROADCAST_EXCHANGE = "broadcast.fanout";
    
    private final ObjectMapper objectMapper;
    
    public BroadcastPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Publish a message to the fanout exchange.
     * All server instances subscribed to this exchange will receive a copy.
     */
    public void publishBroadcast(QueueMessage message, Channel channel) throws Exception {
        try {
            // Ensure the fanout exchange exists
            channel.exchangeDeclare(BROADCAST_EXCHANGE, BuiltinExchangeType.FANOUT, true);
            
            // Convert message to JSON
            String messageJson = objectMapper.writeValueAsString(message);
            byte[] messageBody = messageJson.getBytes();
            
            // Publish to fanout exchange — routing key is ignored for fanout
            channel.basicPublish(
                BROADCAST_EXCHANGE,
                "", // routing key ignored for fanout
                null,
                messageBody
            );
            
            logger.debug("Published broadcast message {} for room {} to exchange {}", 
                        message.getMessageId(), message.getRoomId(), BROADCAST_EXCHANGE);
            
        } catch (IOException e) {
            logger.error("Failed to publish broadcast message: {}", message.getMessageId(), e);
            throw new Exception("Failed to publish broadcast message", e);
        }
    }
}
