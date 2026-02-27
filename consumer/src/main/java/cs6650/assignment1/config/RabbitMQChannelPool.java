package cs6650.assignment1.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

@Component
public class RabbitMQChannelPool {
    
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQChannelPool.class);
    
    @Value("${rabbitmq.host:localhost}")
    private String host;
    
    @Value("${rabbitmq.port:5672}")
    private int port;
    
    @Value("${rabbitmq.username:guest}")
    private String username;
    
    @Value("${rabbitmq.password:guest}")
    private String password;
    
    @Value("${rabbitmq.pool.size:40}")
    private int poolSize;
    
    private Connection connection;
    private BlockingQueue<Channel> channelPool;
    
    @PostConstruct
    public void init() throws IOException, TimeoutException {
        logger.info("Initializing RabbitMQ connection pool...");
        
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        
        // Performance tuning
        factory.setRequestedHeartbeat(30);
        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10000);
        factory.setRequestedChannelMax(0); // No limit on channels
        
        connection = factory.newConnection();
        channelPool = new ArrayBlockingQueue<>(poolSize);
        
        // Pre-create channels for the pool
        for (int i = 0; i < poolSize; i++) {
            Channel channel = connection.createChannel();
            channelPool.offer(channel);
        }
        
        logger.info("RabbitMQ connection pool initialized with {} channels", poolSize);
    }
    
    public Channel borrowChannel() throws InterruptedException {
        Channel channel = channelPool.take();
        
        // Check if channel is still open, create new one if not
        if (!channel.isOpen()) {
            try {
                channel = connection.createChannel();
            } catch (IOException e) {
                logger.error("Failed to create new channel", e);
                throw new RuntimeException("Failed to create RabbitMQ channel", e);
            }
        }
        
        return channel;
    }
    
    public void returnChannel(Channel channel) {
        if (channel != null && channel.isOpen()) {
            channelPool.offer(channel);
        }
    }
    
    public Connection getConnection() {
        return connection;
    }
    
    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up RabbitMQ connection pool...");
        
        // Close all channels in pool
        while (!channelPool.isEmpty()) {
            try {
                Channel channel = channelPool.poll();
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing channel", e);
            }
        }
        
        // Close connection
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                logger.warn("Error closing connection", e);
            }
        }
        
        logger.info("RabbitMQ connection pool cleaned up");
    }
}
