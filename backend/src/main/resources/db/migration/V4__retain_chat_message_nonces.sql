-- Hentikan dan tunggu seluruh transaksi backend lama selesai; penulis lama dapat melewati ledger ini.
CREATE TABLE chat_message_nonces (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    room_id UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    key_version INTEGER NOT NULL CHECK (key_version > 0),
    iv VARCHAR(16) NOT NULL,
    CONSTRAINT uq_chat_message_nonces_room_key_iv UNIQUE (room_id, key_version, iv)
);

INSERT INTO chat_message_nonces (room_id, key_version, iv)
SELECT room_id, key_version, iv FROM chat_messages WHERE iv IS NOT NULL;

ALTER TABLE chat_rooms ADD COLUMN min_message_key_version INTEGER NOT NULL DEFAULT 1;

-- Nonce yang pernah tertimpa tidak bisa dipulihkan; versi kunci tersebut harus dipensiunkan.
UPDATE chat_rooms
SET min_message_key_version = current_key_version + 1
WHERE EXISTS (
    SELECT 1 FROM chat_messages m
    WHERE m.room_id = chat_rooms.id
      AND m.key_version = chat_rooms.current_key_version
      AND (m.edited_at IS NOT NULL OR (m.deleted_at IS NOT NULL AND m.iv IS NULL))
);
