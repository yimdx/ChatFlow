package cs6650.assignment1.consumerv3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cs6650.assignment1.consumerv3.config.AppConfig;
import cs6650.assignment1.consumerv3.db.DatabaseManager;
import cs6650.assignment1.consumerv3.db.DbWriteWorker;
import cs6650.assignment1.consumerv3.db.CircuitBreaker;
import cs6650.assignment1.consumerv3.db.MessageRepository;
import cs6650.assignment1.consumerv3.db.StatsWriteWorker;
import cs6650.assignment1.consumerv3.model.PersistenceStats;
import cs6650.assignment1.consumerv3.model.QueueMessage;
import cs6650.assignment1.consumerv3.queue.BroadcastDispatchWorker;
import cs6650.assignment1.consumerv3.queue.MessageIngestConsumer;
import cs6650.assignment1.consumerv3.queue.RabbitMQConnection;
import cs6650.assignment1.consumerv3.queue.ServerBroadcastClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AppConfig config = new AppConfig();

        String rabbitHost = config.getString("rabbitmq.host", "localhost");
        int rabbitPort = config.getInt("rabbitmq.port", 5672);
        String rabbitUser = config.getString("rabbitmq.username", "guest");
        String rabbitPass = config.getString("rabbitmq.password", "guest");

        int consumerThreadCount = config.getInt("consumer.thread.count", 20);
        int roomCount = config.getInt("room.count", 20);
        int prefetchCount = config.getInt("consumer.prefetch.count", 50);
        int queueCapacity = config.getInt("persist.queue.capacity", 200000);
        int broadcastQueueCapacity = config.getInt("broadcast.queue.capacity", 200000);
        int statsQueueCapacity = config.getInt("stats.queue.capacity", 200000);

        String dbUrl = config.getString("db.url", "jdbc:postgresql://localhost:5432/chatflow");
        String dbUser = config.getString("db.username", "chatflow");
        String dbPassword = config.getString("db.password", "chatflow");
        int dbPoolSize = config.getInt("db.pool.max.size", 30);
        String broadcastServerBaseUrl = config.getString("broadcast.server.base.url", "http://localhost:8082");

        int writerThreads = config.getInt("db.writer.thread.count", 4);
        int statsWriterThreads = config.getInt("stats.writer.thread.count", 2);
        int batchSize = config.getInt("db.batch.size", 100);
        long flushIntervalMs = config.getLong("db.flush.interval.ms", 500);
        int maxRetries = config.getInt("db.retry.max", 3);
        long retryBaseMs = config.getLong("db.retry.base.ms", 200);
        int circuitBreakerThreshold = config.getInt("db.circuit.breaker.failure.threshold", 5);
        long circuitBreakerOpenMs = config.getLong("db.circuit.breaker.open.ms", 5000);
        int broadcastBatchSize = config.getInt("broadcast.batch.size", 50);
        long broadcastFlushIntervalMs = config.getLong("broadcast.flush.interval.ms", 25);

        String dlqName = config.getString("rabbitmq.dlq.name", "chat.persist.dlq");

        logger.info("Starting ChatFlow consumer-v3 with DB persistence");

        RabbitMQConnection rabbitConnection = null;
        DatabaseManager databaseManager = null;
        ServerBroadcastClient broadcastClient = null;
        BroadcastDispatchWorker broadcastWorker = null;
        ExecutorService consumerExecutor = null;
        ExecutorService writerExecutor = null;
        ExecutorService statsExecutor = null;
        ExecutorService broadcastExecutor = null;
        List<MessageIngestConsumer> consumers = new ArrayList<>();
        List<DbWriteWorker> writers = new ArrayList<>();
        List<StatsWriteWorker> statsWriters = new ArrayList<>();

        try {
            rabbitConnection = new RabbitMQConnection(rabbitHost, rabbitPort, rabbitUser, rabbitPass);
            rabbitConnection.connect();

            databaseManager = new DatabaseManager(dbUrl, dbUser, dbPassword, dbPoolSize);
            MessageRepository repository = new MessageRepository(databaseManager.getDataSource());
            repository.initializeSchema();

            BlockingQueue<QueueMessage> persistQueue = new LinkedBlockingQueue<>(queueCapacity);
            BlockingQueue<QueueMessage> broadcastQueue = new LinkedBlockingQueue<>(broadcastQueueCapacity);
            BlockingQueue<QueueMessage> statsQueue = new LinkedBlockingQueue<>(statsQueueCapacity);
            PersistenceStats stats = new PersistenceStats(persistQueue::size, statsQueue::size);
            CircuitBreaker circuitBreaker = new CircuitBreaker(circuitBreakerThreshold, circuitBreakerOpenMs);

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            broadcastClient = new ServerBroadcastClient(broadcastServerBaseUrl, mapper);

            broadcastExecutor = Executors.newSingleThreadExecutor();
            broadcastWorker = new BroadcastDispatchWorker(
                1,
                broadcastQueue,
                broadcastClient,
                broadcastBatchSize,
                broadcastFlushIntervalMs
            );
            broadcastExecutor.submit(broadcastWorker);

            statsExecutor = Executors.newFixedThreadPool(statsWriterThreads);
            for (int i = 0; i < statsWriterThreads; i++) {
                StatsWriteWorker worker = new StatsWriteWorker(
                    i + 1,
                    statsQueue,
                    repository,
                    batchSize,
                    flushIntervalMs
                );
                statsWriters.add(worker);
                statsExecutor.submit(worker);
            }

            writerExecutor = Executors.newFixedThreadPool(writerThreads);
            for (int i = 0; i < writerThreads; i++) {
                DbWriteWorker worker = new DbWriteWorker(
                    i + 1,
                    persistQueue,
                    repository,
                    stats,
                    statsQueue,
                    batchSize,
                    flushIntervalMs,
                    maxRetries,
                    retryBaseMs,
                    circuitBreaker,
                    rabbitConnection,
                    dlqName
                );
                writers.add(worker);
                writerExecutor.submit(worker);
            }

            consumerExecutor = Executors.newFixedThreadPool(consumerThreadCount);
            List<List<String>> distribution = distributeRooms(roomCount, consumerThreadCount);
            for (int i = 0; i < consumerThreadCount; i++) {
                if (distribution.get(i).isEmpty()) {
                    continue;
                }
                MessageIngestConsumer consumer = new MessageIngestConsumer(
                    i + 1,
                    distribution.get(i),
                    rabbitConnection,
                    persistQueue,
                    broadcastQueue,
                    mapper,
                    stats,
                    prefetchCount
                );
                consumers.add(consumer);
                consumerExecutor.submit(consumer);
            }

            final RabbitMQConnection rabbitFinal = rabbitConnection;
            final DatabaseManager dbFinal = databaseManager;
            final ServerBroadcastClient broadcastClientFinal = broadcastClient;
            final BroadcastDispatchWorker broadcastWorkerFinal = broadcastWorker;
            final ExecutorService consumerExecFinal = consumerExecutor;
            final ExecutorService writerExecFinal = writerExecutor;
            final ExecutorService statsExecFinal = statsExecutor;
            final ExecutorService broadcastExecFinal = broadcastExecutor;
            final List<MessageIngestConsumer> consumersFinal = consumers;
            final List<DbWriteWorker> writersFinal = writers;
            final List<StatsWriteWorker> statsWritersFinal = statsWriters;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down consumer-v3");

                for (MessageIngestConsumer consumer : consumersFinal) {
                    consumer.stop();
                }
                for (DbWriteWorker writer : writersFinal) {
                    writer.stop();
                }
                for (StatsWriteWorker worker : statsWritersFinal) {
                    worker.stop();
                }
                if (broadcastWorkerFinal != null) {
                    broadcastWorkerFinal.stop();
                }

                shutdownExecutor(consumerExecFinal);
                shutdownExecutor(writerExecFinal);
                shutdownExecutor(statsExecFinal);
                shutdownExecutor(broadcastExecFinal);

                if (rabbitFinal != null) {
                    rabbitFinal.close();
                }
                if (dbFinal != null) {
                    dbFinal.close();
                }
                if (broadcastClientFinal != null) {
                    // no-op today, but kept for symmetry if the client gains resources later
                }

                logger.info("consumer-v3 shutdown complete");
            }));

            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Fatal error in consumer-v3", e);
            if (consumerExecutor != null) {
                consumerExecutor.shutdownNow();
            }
            if (writerExecutor != null) {
                writerExecutor.shutdownNow();
            }
            if (statsExecutor != null) {
                statsExecutor.shutdownNow();
            }
            if (broadcastExecutor != null) {
                broadcastExecutor.shutdownNow();
            }
            if (rabbitConnection != null) {
                rabbitConnection.close();
            }
            if (databaseManager != null) {
                databaseManager.close();
            }
            if (broadcastClient != null) {
                // no-op today, but kept for symmetry if the client gains resources later
            }
            System.exit(1);
        }
    }

    private static List<List<String>> distributeRooms(int roomCount, int consumerCount) {
        List<List<String>> distribution = new ArrayList<>();
        for (int i = 0; i < consumerCount; i++) {
            distribution.add(new ArrayList<>());
        }
        for (int roomId = 1; roomId <= roomCount; roomId++) {
            distribution.get((roomId - 1) % consumerCount).add(String.valueOf(roomId));
        }
        return distribution;
    }

    private static void shutdownExecutor(ExecutorService executorService) {
        if (executorService == null) {
            return;
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
        }
    }
}
