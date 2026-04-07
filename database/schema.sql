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
);

CREATE TABLE IF NOT EXISTS user_room_activity (
  user_id BIGINT NOT NULL,
  room_id INT NOT NULL,
  first_seen_ts TIMESTAMPTZ NOT NULL,
  last_seen_ts TIMESTAMPTZ NOT NULL,
  message_count BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, room_id)
);

CREATE TABLE IF NOT EXISTS message_minute_stats (
  bucket_minute TIMESTAMPTZ NOT NULL,
  room_id INT NOT NULL,
  user_id BIGINT NOT NULL,
  msg_count BIGINT NOT NULL,
  PRIMARY KEY (bucket_minute, room_id, user_id)
);
