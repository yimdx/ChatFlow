# consumer-v3

Assignment 3 consumer implementation with write-behind DB persistence.

Metrics and analytics retrieval APIs are hosted in `server-v2`.

## Features

- RabbitMQ room queue ingestion (`room.1` ... `room.N`)
- Write-behind pipeline: queue consumers -> in-memory buffer -> DB writer workers -> statistics aggregator workers
- Idempotent message writes (`ON CONFLICT (message_id) DO NOTHING`)
- Duplicate-safe analytics updates (only successfully inserted messages update rollups)
- Retry with exponential backoff and DLQ fallback
- Circuit breaker for repeated database failures
- Database persistence only; metrics retrieval lives in `server-v2`

## Run

```bash
cd consumer-v3
mvn clean package -DskipTests
java -jar target/MessageConsumerV3-1.0-SNAPSHOT.jar
```

## Key Environment Variables

- `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `CONSUMER_THREAD_COUNT`, `CONSUMER_PREFETCH_COUNT`
- `DB_WRITER_THREAD_COUNT`, `DB_BATCH_SIZE`, `DB_FLUSH_INTERVAL_MS`
- `STATS_WRITER_THREAD_COUNT`, `STATS_QUEUE_CAPACITY`
- `DB_RETRY_MAX`, `DB_RETRY_BASE_MS`
- `DB_CIRCUIT_BREAKER_FAILURE_THRESHOLD`, `DB_CIRCUIT_BREAKER_OPEN_MS`
- `API_PORT`

## API Examples

```bash
curl http://localhost:8083/health
curl http://localhost:8083/metrics
curl "http://localhost:8083/api/v1/analytics/active-users?start=2026-03-27T00:00:00Z&end=2026-03-27T01:00:00Z"
curl "http://localhost:8083/api/v1/messages/room/1?start=2026-03-27T00:00:00Z&end=2026-03-27T01:00:00Z&limit=1000&offset=0"
```
