# Chat E2EE API

The server stores encrypted message payloads and wrapped room keys only. Encryption and decryption happen in the client; the server does not receive plaintext chat content or private keys.

All `/api/chat/**` endpoints require an authenticated user. Browser clients must send the Spring Security session cookie and CSRF token for `POST`, `PUT`, and `DELETE` requests. Register a user's ECDH P-256 public key before creating a room.

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

Only encrypted text messages are enabled. `IMAGE` and `FILE` are reserved until an authenticated encrypted-attachment upload/download flow is added.
