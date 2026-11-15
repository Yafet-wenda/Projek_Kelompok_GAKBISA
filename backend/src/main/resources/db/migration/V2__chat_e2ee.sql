-- GAKBISA: fitur chat pribadi & grup dengan enkripsi end-to-end (E2EE).
-- Server HANYA menyimpan ciphertext + kunci yang sudah terbungkus (wrapped).
-- Kunci privat user & kunci ruang (plaintext) tidak pernah sampai ke server.

-- 10. user_keys : kunci publik tiap user (+ cadangan kunci privat yang sudah dienkripsi di client)
CREATE TABLE user_keys (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL REFERENCES users(id),
    public_key            TEXT NOT NULL,                       -- base64 / JWK kunci publik ECDH
    algorithm             VARCHAR(30) NOT NULL DEFAULT 'ECDH-P256',
    -- Opsional: kunci privat dienkripsi di client dengan kunci turunan dari passphrase chat user
    -- (PBKDF2/Argon2). Server tidak bisa membukanya. Dipakai agar bisa login di perangkat lain.
    encrypted_private_key TEXT,
    backup_salt           TEXT,
    backup_iv             TEXT,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at            TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_user_keys_active ON user_keys(user_id) WHERE is_active;

-- 11. chat_rooms : ruang chat pribadi (2 orang) atau grup
CREATE TABLE chat_rooms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                VARCHAR(10) NOT NULL CHECK (type IN ('PRIVATE', 'GROUP')),
    name                VARCHAR(120),                          -- hanya untuk GROUP
    avatar_path         TEXT,
    -- Kunci unik pasangan user untuk chat pribadi: "<uuid kecil>:<uuid besar>".
    -- Mencegah dua ruang PRIVATE untuk pasangan user yang sama.
    direct_key          VARCHAR(73) UNIQUE,
    created_by          UUID NOT NULL REFERENCES users(id),
    current_key_version INTEGER NOT NULL DEFAULT 1 CHECK (current_key_version > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_chat_room_shape CHECK (
        (type = 'PRIVATE' AND direct_key IS NOT NULL)
     OR (type = 'GROUP'   AND direct_key IS NULL AND name IS NOT NULL)
    )
);

-- 12. chat_members : anggota ruang
CREATE TABLE chat_members (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id      UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id),
    role         VARCHAR(10) NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at      TIMESTAMPTZ,                                  -- NULL = masih anggota
    last_read_at TIMESTAMPTZ,                                  -- dasar hitung pesan belum dibaca
    muted        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (room_id, user_id)
);
CREATE INDEX idx_chat_members_user ON chat_members(user_id) WHERE left_at IS NULL;

-- 13. chat_room_keys : kunci simetris ruang (AES-256-GCM) yang dibungkus untuk tiap anggota
--     Cara bungkus (ECIES): client pengirim membuat pasangan kunci sementara, turunkan shared secret
--     dengan kunci publik penerima (ECDH), lalu enkripsi kunci ruang dengan shared secret tsb.
CREATE TABLE chat_room_keys (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id              UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES users(id),   -- penerima
    key_version          INTEGER NOT NULL CHECK (key_version > 0),
    recipient_key_id     UUID NOT NULL REFERENCES user_keys(id), -- kunci publik penerima yang dipakai
    ephemeral_public_key TEXT NOT NULL,
    iv                   TEXT NOT NULL,
    wrapped_key          TEXT NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (room_id, user_id, key_version)
);
CREATE INDEX idx_chat_room_keys_user ON chat_room_keys(user_id, room_id);

-- 14. chat_messages : isi pesan selalu ciphertext
CREATE TABLE chat_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    sender_id       UUID NOT NULL REFERENCES users(id),
    key_version     INTEGER NOT NULL CHECK (key_version > 0),
    message_type    VARCHAR(10) NOT NULL DEFAULT 'TEXT' CHECK (message_type IN ('TEXT', 'IMAGE', 'FILE')),
    ciphertext      TEXT,                                      -- base64, NULL setelah dihapus untuk semua
    iv              TEXT,                                      -- base64 nonce AES-GCM (unik per pesan)
    attachment_path TEXT,                                      -- lokasi file TERENKRIPSI di storage
    reply_to_id     UUID REFERENCES chat_messages(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at       TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT chk_chat_message_payload CHECK (
        deleted_at IS NOT NULL OR (ciphertext IS NOT NULL AND iv IS NOT NULL)
    )
);
CREATE INDEX idx_chat_messages_room_time ON chat_messages(room_id, created_at DESC, id DESC);
CREATE INDEX idx_chat_messages_sender ON chat_messages(sender_id);
