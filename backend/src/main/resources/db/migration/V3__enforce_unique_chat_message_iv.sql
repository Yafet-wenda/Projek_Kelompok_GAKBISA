CREATE UNIQUE INDEX uq_chat_messages_room_key_iv
    ON chat_messages(room_id, key_version, iv)
    WHERE iv IS NOT NULL;
