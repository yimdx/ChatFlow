package cs6650.assignment1.consumerv3.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import cs6650.assignment1.consumerv3.model.PersistenceStats;
import cs6650.assignment1.consumerv3.model.QueueMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class MessageIngestConsumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MessageIngestConsumer.class);

    private final int consumerId;
    private final List<String> roomIds;
    private final RabbitMQConnection rabbitMQConnection;
    private final BlockingQueue<QueueMessage> persistQueue;
    private final BlockingQueue<QueueMessage> broadcastQueue;
    private final ObjectMapper objectMapper;
    private final PersistenceStats stats;
    private final int prefetchCount;
    private volatile boolean running = true;
    private Channel channel;

    public MessageIngestConsumer(
        int consumerId,
        List<String> roomIds,
        RabbitMQConnection rabbitMQConnection,
        BlockingQueue<QueueMessage> persistQueue,
        BlockingQueue<QueueMessage> broadcastQueue,
        ObjectMapper objectMapper,
        PersistenceStats stats,
        int prefetchCount
    ) {
        this.consumerId = consumerId;
        this.roomIds = roomIds;
        this.rabbitMQConnection = rabbitMQConnection;
        this.persistQueue = persistQueue;
        this.broadcastQueue = broadcastQueue;
        this.objectMapper = objectMapper;
        this.stats = stats;
        this.prefetchCount = prefetchCount;
    }

    @Override
    public void run() {
        logger.info("Ingest consumer {} starting for rooms {}", consumerId, roomIds);
        try {
            channel = rabbitMQConnection.createChannel();
            channel.basicQos(prefetchCount);

            for (String roomId : roomIds) {
                String queueName = "room." + roomId;
                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    long tag = delivery.getEnvelope().getDeliveryTag();
                    try {
                        QueueMessage message = objectMapper.readValue(
                            new String(delivery.getBody(), StandardCharsets.UTF_8),
                            QueueMessage.class
                        );
                        stats.incReceived();

                        boolean offered = persistQueue.offer(message);
                        boolean broadcastOffered = broadcastQueue.offer(message);
                        if (offered && broadcastOffered) {
                            channel.basicAck(tag, false);
                        } else {
                            if (!broadcastOffered) {
                                logger.warn("Broadcast queue full for message {}, sending NACK for retry", message.getMessageId());
                            }
                            stats.incQueueFull();
                            channel.basicNack(tag, false, true);
                        }
                    } catch (Exception e) {
                        logger.error("Consumer {} failed to parse/process message", consumerId, e);
                        channel.basicNack(tag, false, false);
                    }
                };

                channel.basicConsume(queueName, false, deliverCallback, consumerTag -> {});
            }

            while (running) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            logger.error("Consumer {} crashed", consumerId, e);
        } finally {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (Exception e) {
                    logger.warn("Failed to close channel for consumer {}", consumerId, e);
                }
            }
        }

        logger.info("Ingest consumer {} stopped", consumerId);
    }

    public void stop() {
        running = false;
    }
}
