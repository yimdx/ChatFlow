package cs6650.assignment1.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageRepository {
    private final DataSource dataSource;

    public MessageRepository(DataSource dataSource) {
        this.dataSource = dataSource;
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
              AND (CAST(? AS TIMESTAMPTZ) IS NULL OR message_ts >= CAST(? AS TIMESTAMPTZ))
              AND (CAST(? AS TIMESTAMPTZ) IS NULL OR message_ts <= CAST(? AS TIMESTAMPTZ))
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

    private static List<Map<String, Object>> toMapList(ResultSet resultSet) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        int columnCount = resultSet.getMetaData().getColumnCount();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(resultSet.getMetaData().getColumnLabel(i), resultSet.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }
}
