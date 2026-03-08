package cs6650.assignment1.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import cs6650.assignment1.config.RabbitMQChannelPool;
import cs6650.assignment1.model.QueueMessage;
import cs6650.assignment1.queue.BroadcastPublisher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class MessageConsumerService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageConsumerService.class);
    
    @Autowired
    private RabbitMQChannelPool channelPool;
    
    @Autowired
    private RoomManager roomManager;

    @Autowired
    private BroadcastPublisher broadcastPublisher;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${consumer.thread.count:20}")
    private int consumerThreadCount;
    
    @Value("${consumer.prefetch.count:10}")
    private int prefetchCount;
    
    @Value("${chat.room.count:20}")
    private int roomCount;
    
    private ExecutorService executorService;
    private List<ConsumerWorker> workers = new ArrayList<>();
    private volatile boolean running = true;
    
    @PostConstruct
    public void startConsumers() {
        logger.info("Starting {} consumer threads...", consumerThreadCount);
        
        executorService = Executors.newFixedThreadPool(consumerThreadCount);
        
        // Distribute rooms across consumer threads
        int roomsPerThread = (int) Math.ceil((double) roomCount / consumerThreadCount);
        
        for (int i = 0; i < consumerThreadCount; i++) {
            int startRoom = i * roomsPerThread + 1;
            int endRoom = Math.min(startRoom + roomsPerThread - 1, roomCount);
            
            if (startRoom <= roomCount) {
                List<String> assignedQueues = new ArrayList<>();
                for (int roomId = startRoom; roomId <= endRoom; roomId++) {
                    assignedQueues.add("room." + roomId);
                }
                
                ConsumerWorker worker = new ConsumerWorker(i, assignedQueues);
                workers.add(worker);
                executorService.submit(worker);
                
                logger.info("Consumer thread {} assigned queues: {}", i, assignedQueues);
            }
        }
        
        logger.info("All consumer threads started");
    }
    
    @PreDestroy
    public void stopConsumers() {
        logger.info("Stopping consumer threads...");
        running = false;
        
        // Cancel all consumers
        for (ConsumerWorker worker : workers) {
            worker.stop();
        }
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        
        logger.info("All consumer threads stopped");
    }
    
    private class ConsumerWorker implements Runnable {
        private final int workerId;
        private final List<String> assignedQueues;
        private Channel channel;
        private final List<String> consumerTags = new ArrayList<>();
        
        public ConsumerWorker(int workerId, List<String> assignedQueues) {
            this.workerId = workerId;
            this.assignedQueues = assignedQueues;
        }
        
        @Override
        public void run() {
            try {
                channel = channelPool.borrowChannel();
                channel.basicQos(prefetchCount);
                
                for (String queueName : assignedQueues) {
                    DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                        try {
                            String messageBody = new String(delivery.getBody(), "UTF-8");
                            processMessage(messageBody, queueName);
                            
                            // Acknowledge message after successful processing
                            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                            roomManager.incrementMessagesProcessed();
                            
                        } catch (Exception e) {
                            logger.error("Error processing message from queue {}", queueName, e);
                            roomManager.incrementMessagesFailed();
                            
                            // Reject and requeue the message (or send to DLQ)
                            try {
                                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                            } catch (IOException ioException) {
                                logger.error("Failed to nack message", ioException);
                            }
                        }
                    };
                    
                    String consumerTag = channel.basicConsume(queueName, false, deliverCallback, 
                                                             tag -> logger.info("Consumer {} cancelled", tag));
                    consumerTags.add(consumerTag);
                    logger.info("Worker {} started consuming from queue: {}", workerId, queueName);
                }
                
                // Keep the thread alive
                while (running && channel.isOpen()) {
                    Thread.sleep(1000);
                }
                
            } catch (Exception e) {
                logger.error("Consumer worker {} encountered an error", workerId, e);
            } finally {
                if (channel != null) {
                    channelPool.returnChannel(channel);
                }
            }
        }
        
        public void stop() {
            try {
                if (channel != null && channel.isOpen()) {
                    for (String tag : consumerTags) {
                        channel.basicCancel(tag);
                    }
                }
            } catch (IOException e) {
                logger.error("Error cancelling consumer", e);
            }
        }
    }
    
    private void processMessage(String messageBody, String queueName) {
        try {
            QueueMessage message = objectMapper.readValue(messageBody, QueueMessage.class);

            logger.debug("Processing message {} from queue {} for room {}",
                        message.getMessageId(), queueName, message.getRoomId());

            broadcastToServers(message);
            
        } catch (Exception e) {
            logger.error("Failed to process message from queue {}: {}", queueName, messageBody, e);
            throw new RuntimeException("Failed to process message", e);
        }
    }
    
    private void broadcastToServers(QueueMessage message) {
        try {
            // Get a channel from the pool to publish the broadcast message
            Channel channel = channelPool.borrowChannel();
            
            try {
                // Publish message to broadcast queue for servers to consume
                broadcastPublisher.publishBroadcast(message, channel);
                
                logger.debug("Sent message {} to broadcast queue for room {}", 
                            message.getMessageId(), message.getRoomId());
                
            } finally {
                channelPool.returnChannel(channel);
            }
            
        } catch (Exception e) {
            logger.error("Failed to broadcast message {} to servers for room {}",
                        message.getMessageId(), message.getRoomId(), e);
            throw new RuntimeException("Failed to broadcast message to servers", e);
        }
    }
}
