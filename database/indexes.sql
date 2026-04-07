CREATE INDEX IF NOT EXISTS idx_messages_room_ts
  ON chat_messages (room_id, message_ts);

CREATE INDEX IF NOT EXISTS idx_messages_user_ts
  ON chat_messages (user_id, message_ts DESC);

CREATE INDEX IF NOT EXISTS idx_messages_ts_user
  ON chat_messages (message_ts, user_id);

CREATE INDEX IF NOT EXISTS idx_activity_user_last
  ON user_room_activity (user_id, last_seen_ts DESC);
