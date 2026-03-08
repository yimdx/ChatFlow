package cs6650.assignment1.queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeoutException;

public class RabbitMQChannelPool {
    
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQChannelPool.class);
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final int poolSize;
    
    private Connection connection;
    private BlockingQueue<Channel> channelPool;
    
    public RabbitMQChannelPool(String host, int port, String username, String password, int poolSize) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
    }
    
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
