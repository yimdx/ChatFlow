package cs6650.assignment1.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cs6650.assignment1.db.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsApiServer {
    private static final Logger logger = LoggerFactory.getLogger(MetricsApiServer.class);

    private final HttpServer server;
    private final MessageRepository repository;
    private final ObjectMapper objectMapper;
    private final ResponseCache responseCache;

    public MetricsApiServer(int port, MessageRepository repository) throws IOException {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.responseCache = new ResponseCache(Duration.ofSeconds(5));
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        registerRoutes();
    }

    private void registerRoutes() {
        server.createContext("/health", this::handleHealth);
        server.createContext("/metrics", this::handleMetrics);
        server.createContext("/api/v1/messages/room", this::handleMessagesByRoom);
        server.createContext("/api/v1/messages/user", this::handleMessagesByUser);
        server.createContext("/api/v1/analytics/active-users", this::handleActiveUsers);
        server.createContext("/api/v1/analytics/user-rooms", this::handleUserRooms);
        server.createContext("/api/v1/analytics/top-users", this::handleTopUsers);
        server.createContext("/api/v1/analytics/top-rooms", this::handleTopRooms);
        server.createContext("/api/v1/analytics/messages-rate", this::handleMessageRate);
        server.createContext("/api/v1/analytics/participation", this::handleParticipation);
    }

    public void start() {
        server.start();
        logger.info("Metrics API server started on port {}", server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        writeJson(exchange, 200, Map.of("status", "healthy"));
    }

    private void handleMetrics(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant end = parseInstant(query.get("end"), Instant.now());
            Instant start = parseInstant(query.get("start"), end.minusSeconds(3600));
            int sampleLimit = parseInt(query.get("sampleLimit"), 100);

            List<Map<String, Object>> topUsers = repository.getTopUsers(start, end, 10);
            List<Map<String, Object>> topRooms = repository.getTopRooms(start, end, 10);
            Long sampleUserId = extractLong(topUsers, "user_id");
            Integer sampleRoomId = extractInt(topRooms, "room_id");

            Map<String, Object> coreQueries = new LinkedHashMap<>();
            coreQueries.put("activeUsers", repository.countActiveUsers(start, end));
            coreQueries.put("sampleUserId", sampleUserId);
            coreQueries.put("sampleRoomId", sampleRoomId);
            coreQueries.put("messagesByRoom", sampleRoomId == null
                ? List.of()
                : repository.getMessagesByRoom(sampleRoomId, start, end, sampleLimit, 0));
            coreQueries.put("messagesByUser", sampleUserId == null
                ? List.of()
                : repository.getMessagesByUser(sampleUserId, start, end, sampleLimit, 0));
            coreQueries.put("userRooms", sampleUserId == null
                ? List.of()
                : repository.getUserRooms(sampleUserId));

            Map<String, Object> analytics = new LinkedHashMap<>();
            analytics.put("messageRatePerMinute", repository.getMessageRate(start, end, "minute"));
            analytics.put("messageRatePerSecond", repository.getMessageRate(start, end, "second"));
            analytics.put("topUsers", topUsers);
            analytics.put("topRooms", topRooms);
            analytics.put("participationPatterns", repository.getParticipationPatterns(start, end, 10));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", "ok");
            payload.put("window", Map.of("start", start, "end", end));
            payload.put("coreQueries", coreQueries);
            payload.put("analytics", analytics);

            writeJson(exchange, 200, payload);
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleMessagesByRoom(HttpExchange exchange) throws IOException {
        try {
            int roomId = parsePathTailAsInt(exchange.getRequestURI(), "/api/v1/messages/room/");
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstant(query.get("start"), Instant.now().minusSeconds(3600));
            Instant end = parseInstant(query.get("end"), Instant.now());
            int limit = parseInt(query.get("limit"), 1000);
            int offset = parseInt(query.get("offset"), 0);
            writeJson(exchange, 200, repository.getMessagesByRoom(roomId, start, end, limit, offset));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleMessagesByUser(HttpExchange exchange) throws IOException {
        try {
            long userId = parsePathTailAsLong(exchange.getRequestURI(), "/api/v1/messages/user/");
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstantOrNull(query.get("start"));
            Instant end = parseInstantOrNull(query.get("end"));
            int limit = parseInt(query.get("limit"), 1000);
            int offset = parseInt(query.get("offset"), 0);
            writeJson(exchange, 200, repository.getMessagesByUser(userId, start, end, limit, offset));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleActiveUsers(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstant(query.get("start"), Instant.now().minusSeconds(3600));
            Instant end = parseInstant(query.get("end"), Instant.now());
            long count = repository.countActiveUsers(start, end);
            writeJson(exchange, 200, Map.of("activeUsers", count, "start", start, "end", end));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleUserRooms(HttpExchange exchange) throws IOException {
        try {
            long userId = parsePathTailAsLong(exchange.getRequestURI(), "/api/v1/analytics/user-rooms/");
            writeJson(exchange, 200, repository.getUserRooms(userId));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleTopUsers(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstant(query.get("start"), Instant.now().minusSeconds(3600));
            Instant end = parseInstant(query.get("end"), Instant.now());
            int n = parseInt(query.get("n"), 10);
            writeJson(exchange, 200, repository.getTopUsers(start, end, n));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleTopRooms(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstant(query.get("start"), Instant.now().minusSeconds(3600));
            Instant end = parseInstant(query.get("end"), Instant.now());
            int n = parseInt(query.get("n"), 10);
            writeJson(exchange, 200, repository.getTopRooms(start, end, n));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleMessageRate(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstant(query.get("start"), Instant.now().minusSeconds(3600));
            Instant end = parseInstant(query.get("end"), Instant.now());
            String granularity = query.getOrDefault("granularity", "minute");
            writeJson(exchange, 200, repository.getMessageRate(start, end, granularity));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void handleParticipation(HttpExchange exchange) throws IOException {
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI());
            Instant start = parseInstant(query.get("start"), Instant.now().minusSeconds(3600));
            Instant end = parseInstant(query.get("end"), Instant.now());
            int n = parseInt(query.get("n"), 20);
            writeJson(exchange, 200, repository.getParticipationPatterns(start, end, n));
        } catch (Exception e) {
            writeError(exchange, e);
        }
    }

    private void writeError(HttpExchange exchange, Exception e) throws IOException {
        logger.error("API error", e);
        int status = e instanceof SQLException ? 500 : 400;
        writeJson(exchange, status, Map.of("error", e.getMessage()));
    }

    private void writeJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        String cacheKey = statusCode == 200 ? exchange.getRequestURI().toString() : null;
        byte[] body = cacheKey == null
            ? objectMapper.writeValueAsBytes(payload)
            : responseCache.getOrCompute(cacheKey, () -> {
                try {
                    return objectMapper.writeValueAsBytes(payload);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> queryPairs = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return queryPairs;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                queryPairs.put(key, value);
            }
        }
        return queryPairs;
    }

    private static int parsePathTailAsInt(URI uri, String prefix) {
        String path = uri.getPath();
        if (!path.startsWith(prefix)) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        return Integer.parseInt(path.substring(prefix.length()));
    }

    private static long parsePathTailAsLong(URI uri, String prefix) {
        String path = uri.getPath();
        if (!path.startsWith(prefix)) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        return Long.parseLong(path.substring(prefix.length()));
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static Instant parseInstant(String value, Instant defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Instant.parse(value);
    }

    private static Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static Long extractLong(List<Map<String, Object>> rows, String key) {
        if (rows.isEmpty()) {
            return null;
        }
        Object value = rows.get(0).get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(value.toString());
    }

    private static Integer extractInt(List<Map<String, Object>> rows, String key) {
        if (rows.isEmpty()) {
            return null;
        }
        Object value = rows.get(0).get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.parseInt(value.toString());
    }
}
