package cs6650.assignment1.consumerv3.queue;

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
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(username);
        factory.setPassword(password);
        factory.setRequestedHeartbeat(30);
        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10000);

        connection = factory.newConnection();
        logger.info("Connected to RabbitMQ {}:{}", host, port);
    }

    public Channel createChannel() throws IOException {
        if (connection == null || !connection.isOpen()) {
            throw new IOException("RabbitMQ connection is not open");
        }
        return connection.createChannel();
    }

    public void publishToDlq(String dlqName, String routingKey, byte[] body) {
        try (Channel channel = createChannel()) {
            channel.queueDeclare(dlqName, true, false, false, null);
            channel.basicPublish("", routingKey, null, body);
        } catch (Exception e) {
            logger.error("Failed to publish to DLQ {}", dlqName, e);
        }
    }

    public void close() {
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                logger.warn("Error closing RabbitMQ connection", e);
            }
        }
    }
}
