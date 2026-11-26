# Chat E2EE API

The server stores encrypted message payloads and wrapped room keys only. Encryption and decryption happen in the client; the server does not receive plaintext chat content or private keys.

All `/api/chat/**` endpoints use the same stateless JWT Bearer authentication as the rest of the REST API. Send `Authorization: Bearer <accessToken>` on every request; obtain the token from `POST /api/auth/login`. API requests do not use a Spring Security session cookie or CSRF token. Browser clients must allow the API's configured CORS origin and send the `Authorization` header. Register a user's ECDH P-256 public key before creating a room.

## Client cryptography

- `publicKey` and `ephemeralPublicKey` are standard Base64 encodings of the 65-byte uncompressed P-256 public point exported by Web Crypto.
- To wrap a room key, generate an ephemeral ECDH P-256 key pair, derive an AES-256-GCM key with Web Crypto `deriveKey`, and encrypt the 32-byte room key with a fresh random 12-byte IV. `wrappedKey` is Base64 ciphertext including the 16-byte GCM authentication tag.
- Message `ciphertext` is Base64 AES-GCM output (including its authentication tag); `iv` is a fresh random 12-byte value for every message or edit.
- The browser creates a PKCS#8 private-key backup encrypted with AES-256-GCM. Its AES key is derived from the user's passphrase (minimum 12 characters in the SPA) with PBKDF2-HMAC-SHA-256 (310,000 iterations), a random 16-byte salt, and a random 12-byte IV. The AES-GCM additional authenticated data is the UTF-8 encoding of `gakbisa-e2ee-private-key-v1:` followed by the Base64 public key. The passphrase is never sent to the server. Keep the passphrase safe: the server cannot recover the private key without it.
- The non-extractable private key is stored in the browser's IndexedDB. Clearing browser data requires restoring from the encrypted backup.
- The server controls which public keys it returns. Compare each member's displayed SHA-256 key fingerprint over a separate trusted channel before sending sensitive messages; this SPA displays fingerprints but does not yet persist a verified-key state.

## Endpoints

- `POST /api/chat/keys` — register or rotate the caller's public key. An optional encrypted private-key backup must include `encryptedPrivateKey`, `backupSalt`, and `backupIv` together.
- `GET /api/chat/keys/me` — read the caller's public key and encrypted backup.
- `GET /api/chat/keys/users/{userId}` — read another active user's public key.
- `POST /api/chat/rooms/direct` — create or return a private room. The request supplies one wrapped room key for each participant.
- `POST /api/chat/rooms/group` — create a group with wrapped keys for the creator and every initial member.
- `GET /api/chat/rooms` — list the caller's active rooms and unread counts.
- `GET /api/chat/rooms/{roomId}` — read room members, their active public keys, and the caller's room-key envelopes.
- `PUT /api/chat/rooms/{roomId}/keys` — rotate the room key; supply a new envelope for every active member.
- `POST /api/chat/rooms/{roomId}/members?userId=...` — add a group member and rotate keys for all active members.
- `POST /api/chat/rooms/{roomId}/members/{userId}/remove` — remove a member and rotate keys for the remaining members.
- `POST /api/chat/rooms/{roomId}/leave` — leave a group and rotate keys for the remaining members.
- `GET /api/chat/rooms/{roomId}/messages` — read message history, up to 100 messages per page. For the next page, send both `beforeAt` and `beforeId` from the last message.
- `POST /api/chat/rooms/{roomId}/messages` — send ciphertext using the current room-key version.
- `PUT /api/chat/rooms/{roomId}/messages/{messageId}` — edit the caller's message with new ciphertext and a new IV.
- `DELETE /api/chat/rooms/{roomId}/messages/{messageId}` — delete the caller's message for everyone.
- `POST /api/chat/rooms/{roomId}/read` — mark the room as read.

For room-key envelopes, `keys` must contain exactly one entry per recipient. Each entry supplies `userId`, the recipient's active `recipientKeyId`, `ephemeralPublicKey`, a Base64 12-byte AES-GCM `iv`, and `wrappedKey`. Message `ciphertext` and `iv` are Base64; the server rejects an IV reused within the same room-key version.

When a user key is rotated, clients should rotate room keys in every active room so the new public key can receive the wrapped room key. A member added later receives only the new room-key version; members removed from a group are excluded from the new version.

`TEXT`, `IMAGE`, and `FILE` messages are supported. Images and files are packaged with their filename, media type, and optional caption, encrypted in the browser with the current room AES-GCM key, and stored inline as Base64 ciphertext in the message row; the server does not receive plaintext file bytes or a storage path. The SPA limits raw files to 1 MB so the Base64 JSON payload fits the existing 2,000,000-character ciphertext limit. Large-file streaming/storage is not implemented. The SPA polls for updates every five seconds; WebSocket delivery is not implemented.
