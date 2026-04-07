package cs6650.assignment1.consumerv3.db;

import cs6650.assignment1.consumerv3.model.QueueMessage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MessageRepository {
    private final DataSource dataSource;

    public MessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void initializeSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                  message_id UUID PRIMARY KEY,
                  room_id INT NOT NULL,
                  user_id BIGINT NOT NULL,
                  username VARCHAR(64) NOT NULL,
                  message_type VARCHAR(16) NOT NULL,
                  message_text TEXT NOT NULL,
                  message_ts TIMESTAMPTZ NOT NULL,
                  server_id VARCHAR(64) NOT NULL,
                  client_ip INET,
                  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS user_room_activity (
                  user_id BIGINT NOT NULL,
                  room_id INT NOT NULL,
                  first_seen_ts TIMESTAMPTZ NOT NULL,
                  last_seen_ts TIMESTAMPTZ NOT NULL,
                  message_count BIGINT NOT NULL DEFAULT 0,
                  PRIMARY KEY (user_id, room_id)
                )
                """);
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS message_minute_stats (
                  bucket_minute TIMESTAMPTZ NOT NULL,
                  room_id INT NOT NULL,
                  user_id BIGINT NOT NULL,
                  msg_count BIGINT NOT NULL,
                  PRIMARY KEY (bucket_minute, room_id, user_id)
                )
                """);

            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_room_ts ON chat_messages (room_id, message_ts)");
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_user_ts ON chat_messages (user_id, message_ts DESC)");
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_messages_ts_user ON chat_messages (message_ts, user_id)");
            connection.createStatement().execute("CREATE INDEX IF NOT EXISTS idx_activity_user_last ON user_room_activity (user_id, last_seen_ts DESC)");
        }
    }

    public List<QueueMessage> persistBatch(List<QueueMessage> messages) throws SQLException {
        if (messages.isEmpty()) {
            return List.of();
        }

        String insertMessageSql = """
            INSERT INTO chat_messages (
                message_id, room_id, user_id, username, message_type, message_text,
                message_ts, server_id, client_ip
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS inet))
            ON CONFLICT (message_id) DO NOTHING
            """;

        List<QueueMessage> insertedMessages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement insertMessageStmt = connection.prepareStatement(insertMessageSql)) {

                for (QueueMessage message : messages) {
                    Instant ts = message.getTimestamp() != null ? message.getTimestamp() : Instant.now();
                    Timestamp messageTs = Timestamp.from(ts);

                    insertMessageStmt.setObject(1, parseUuid(message.getMessageId()));
                    insertMessageStmt.setInt(2, parseInt(message.getRoomId()));
                    insertMessageStmt.setLong(3, parseLong(message.getUserId()));
                    insertMessageStmt.setString(4, safe(message.getUsername()));
                    insertMessageStmt.setString(5, safe(message.getMessageType()));
                    insertMessageStmt.setString(6, safe(message.getMessage()));
                    insertMessageStmt.setTimestamp(7, messageTs);
                    insertMessageStmt.setString(8, safe(message.getServerId()));
                    insertMessageStmt.setString(9, nullable(message.getClientIp()));
                    insertMessageStmt.addBatch();
                }

                int[] messageResults = insertMessageStmt.executeBatch();
                connection.commit();

                for (int i = 0; i < messageResults.length; i++) {
                    int result = messageResults[i];
                    if (result > 0 || result == PreparedStatement.SUCCESS_NO_INFO) {
                        insertedMessages.add(messages.get(i));
                    }
                }
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }

        return insertedMessages;
    }

    public void updateAnalyticsBatch(List<QueueMessage> messages) throws SQLException {
        if (messages.isEmpty()) {
            return;
        }

        String upsertUserActivitySql = """
            INSERT INTO user_room_activity (
                user_id, room_id, first_seen_ts, last_seen_ts, message_count
            ) VALUES (?, ?, ?, ?, 1)
            ON CONFLICT (user_id, room_id)
            DO UPDATE SET
              first_seen_ts = LEAST(user_room_activity.first_seen_ts, EXCLUDED.first_seen_ts),
              last_seen_ts = GREATEST(user_room_activity.last_seen_ts, EXCLUDED.last_seen_ts),
              message_count = user_room_activity.message_count + 1
            """;

        String upsertMinuteStatsSql = """
            INSERT INTO message_minute_stats (
                bucket_minute, room_id, user_id, msg_count
            ) VALUES (?, ?, ?, 1)
            ON CONFLICT (bucket_minute, room_id, user_id)
            DO UPDATE SET msg_count = message_minute_stats.msg_count + 1
            """;

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsertActivityStmt = connection.prepareStatement(upsertUserActivitySql);
                 PreparedStatement upsertMinuteStmt = connection.prepareStatement(upsertMinuteStatsSql)) {

                for (QueueMessage message : messages) {
                    Instant ts = message.getTimestamp() != null ? message.getTimestamp() : Instant.now();
                    Timestamp messageTs = Timestamp.from(ts);
                    Timestamp bucketTs = Timestamp.from(ts.truncatedTo(ChronoUnit.MINUTES));

                    upsertActivityStmt.setLong(1, parseLong(message.getUserId()));
                    upsertActivityStmt.setInt(2, parseInt(message.getRoomId()));
                    upsertActivityStmt.setTimestamp(3, messageTs);
                    upsertActivityStmt.setTimestamp(4, messageTs);
                    upsertActivityStmt.addBatch();

                    upsertMinuteStmt.setTimestamp(1, bucketTs);
                    upsertMinuteStmt.setInt(2, parseInt(message.getRoomId()));
                    upsertMinuteStmt.setLong(3, parseLong(message.getUserId()));
                    upsertMinuteStmt.addBatch();
                }

                upsertActivityStmt.executeBatch();
                upsertMinuteStmt.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<Map<String, Object>> getMessagesByRoom(int roomId, Instant start, Instant end, int limit, int offset) throws SQLException {
        String sql = """
            SELECT message_id, room_id, user_id, username, message_type, message_text, message_ts
            FROM chat_messages
            WHERE room_id = ? AND message_ts >= ? AND message_ts <= ?
            ORDER BY message_ts ASC
            LIMIT ? OFFSET ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, roomId);
            stmt.setTimestamp(2, Timestamp.from(start));
            stmt.setTimestamp(3, Timestamp.from(end));
            stmt.setInt(4, limit);
            stmt.setInt(5, offset);
            return toMapList(stmt.executeQuery());
        }
    }

    public List<Map<String, Object>> getMessagesByUser(long userId, Instant start, Instant end, int limit, int offset) throws SQLException {
        String sql = """
            SELECT message_id, room_id, user_id, username, message_type, message_text, message_ts
            FROM chat_messages
            WHERE user_id = ?
              AND (? IS NULL OR message_ts >= ?)
              AND (? IS NULL OR message_ts <= ?)
            ORDER BY message_ts DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            if (start == null) {
                stmt.setTimestamp(2, null);
                stmt.setTimestamp(3, null);
            } else {
                Timestamp ts = Timestamp.from(start);
                stmt.setTimestamp(2, ts);
                stmt.setTimestamp(3, ts);
            }
            if (end == null) {
                stmt.setTimestamp(4, null);
                stmt.setTimestamp(5, null);
            } else {
                Timestamp ts = Timestamp.from(end);
                stmt.setTimestamp(4, ts);
                stmt.setTimestamp(5, ts);
            }
            stmt.setInt(6, limit);
            stmt.setInt(7, offset);
            return toMapList(stmt.executeQuery());
        }
    }

    public long countActiveUsers(Instant start, Instant end) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT user_id) AS active_users FROM chat_messages WHERE message_ts >= ? AND message_ts <= ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(start));
            stmt.setTimestamp(2, Timestamp.from(end));
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getLong("active_users");
        }
    }

    public List<Map<String, Object>> getUserRooms(long userId) throws SQLException {
        String sql = "SELECT room_id, last_seen_ts, message_count FROM user_room_activity WHERE user_id = ? ORDER BY last_seen_ts DESC";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            return toMapList(stmt.executeQuery());
        }
    }

    public List<Map<String, Object>> getTopUsers(Instant start, Instant end, int n) throws SQLException {
        String sql = """
            SELECT user_id, COUNT(*) AS total_messages
            FROM chat_messages
            WHERE message_ts BETWEEN ? AND ?
            GROUP BY user_id
            ORDER BY total_messages DESC
            LIMIT ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(start));
            stmt.setTimestamp(2, Timestamp.from(end));
            stmt.setInt(3, n);
            return toMapList(stmt.executeQuery());
        }
    }

    public List<Map<String, Object>> getTopRooms(Instant start, Instant end, int n) throws SQLException {
        String sql = """
            SELECT room_id, COUNT(*) AS total_messages
            FROM chat_messages
            WHERE message_ts BETWEEN ? AND ?
            GROUP BY room_id
            ORDER BY total_messages DESC
            LIMIT ?
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(start));
            stmt.setTimestamp(2, Timestamp.from(end));
            stmt.setInt(3, n);
            return toMapList(stmt.executeQuery());
        }
    }

    public List<Map<String, Object>> getMessageRate(Instant start, Instant end, String granularity) throws SQLException {
        String bucket = "minute".equalsIgnoreCase(granularity) ? "minute" : "second";
        String sql = """
            SELECT date_trunc(?, message_ts) AS bucket, COUNT(*) AS count
            FROM chat_messages
            WHERE message_ts BETWEEN ? AND ?
            GROUP BY bucket
            ORDER BY bucket ASC
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, bucket);
            stmt.setTimestamp(2, Timestamp.from(start));
            stmt.setTimestamp(3, Timestamp.from(end));
            return toMapList(stmt.executeQuery());
        }
    }

    public List<Map<String, Object>> getParticipationPatterns(Instant start, Instant end, int n) throws SQLException {
        String sql = """
            SELECT user_id,
                   COUNT(DISTINCT room_id) AS rooms_joined,
                   MIN(message_ts) AS first_activity,
                   MAX(message_ts) AS last_activity,
                   COUNT(*) AS total_messages
            FROM chat_messages
            WHERE message_ts BETWEEN ? AND ?
            GROUP BY user_id
            ORDER BY total_messages DESC
            LIMIT ?
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(start));
            stmt.setTimestamp(2, Timestamp.from(end));
            stmt.setInt(3, n);
            return toMapList(stmt.executeQuery());
        }
    }

    private static List<Map<String, Object>> toMapList(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        int columnCount = resultSet.getMetaData().getColumnCount();

        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = resultSet.getMetaData().getColumnLabel(i);
                row.put(columnName, resultSet.getObject(i));
            }
            rows.add(row);
        }

        return rows;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            return UUID.randomUUID();
        }
    }
}
