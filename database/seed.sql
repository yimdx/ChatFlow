-- Optional seed script for local validation.
INSERT INTO chat_messages (
  message_id, room_id, user_id, username, message_type, message_text, message_ts, server_id, client_ip
)
VALUES (
  gen_random_uuid(), 1, 1001, 'seed-user', 'TEXT', 'hello from seed', NOW(), 'seed-server', '127.0.0.1'
)
ON CONFLICT DO NOTHING;
