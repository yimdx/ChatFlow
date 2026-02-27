package cs6650.assignment1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import cs6650.assignment1.config.RabbitMQChannelPool;
import cs6650.assignment1.config.RabbitMQSetup;
import cs6650.assignment1.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Service
public class MessagePublisherService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessagePublisherService.class);
    
    @Autowired
    private RabbitMQChannelPool channelPool;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${server.id:server-1}")
    private String serverId;
    
    private String serverHostName;
    
    public MessagePublisherService() {
        try {
            this.serverHostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            this.serverHostName = "unknown";
        }
    }
    
    public void publishMessage(QueueMessage message) throws Exception {
        Channel channel = null;
        try {
            channel = channelPool.borrowChannel();
            
            // Enable publisher confirms for reliability
            channel.confirmSelect();
            
            // Convert message to JSON
            String messageJson = objectMapper.writeValueAsString(message);
            byte[] messageBody = messageJson.getBytes();
            
            // Determine routing key based on room
            String routingKey = RabbitMQSetup.getRoutingKey(message.getRoomId());
            
            // Publish message with persistent delivery mode
            channel.basicPublish(
                RabbitMQSetup.EXCHANGE_NAME,
                routingKey,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                messageBody
            );
            
            // Wait for confirmation
            channel.waitForConfirmsOrDie(5000);
            
            logger.debug("Published message {} to room {} with routing key {}", 
                        message.getMessageId(), message.getRoomId(), routingKey);
            
        } catch (IOException | InterruptedException e) {
            logger.error("Failed to publish message: {}", message.getMessageId(), e);
            throw new Exception("Failed to publish message to queue", e);
        } finally {
            if (channel != null) {
                channelPool.returnChannel(channel);
            }
        }
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public String getServerHostName() {
        return serverHostName;
    }
}
