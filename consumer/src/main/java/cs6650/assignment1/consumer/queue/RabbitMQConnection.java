package cs6650.assignment1.consumer.queue;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class RabbitMQConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConnection.class);
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    
    private Connection connection;
    
    public RabbitMQConnection(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }
    
    public void connect() throws IOException, TimeoutException {
        logger.info("Connecting to RabbitMQ at {}:{}...", host, port);
        
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
        
        logger.info("Successfully connected to RabbitMQ");
    }
    
    public Channel createChannel() throws IOException {
        if (connection == null || !connection.isOpen()) {
            throw new IOException("Connection is not established");
        }
        return connection.createChannel();
    }
    
    public void close() {
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
                logger.info("RabbitMQ connection closed");
            } catch (IOException e) {
                logger.error("Error closing RabbitMQ connection", e);
            }
        }
    }
    
    public boolean isConnected() {
        return connection != null && connection.isOpen();
    }
}
