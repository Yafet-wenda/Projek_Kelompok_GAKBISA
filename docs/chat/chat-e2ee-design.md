# Desain Chat E2EE GAKBISA (model & database)

## Prinsip
Server hanya menyimpan **ciphertext** dan **kunci yang sudah dibungkus**. Enkripsi/dekripsi dilakukan di client (Web Crypto API), jadi server tidak bisa membaca isi chat.

## Alur kunci
1. **Registrasi / pertama buka chat**: client membuat pasangan kunci ECDH P-256. Kunci publik dikirim ke server (`user_keys.public_key`). Kunci privat disimpan di client (IndexedDB). Opsional: cadangan kunci privat yang dienkripsi dengan passphrase (`encrypted_private_key`, `backup_salt`, `backup_iv`) supaya bisa dipakai di perangkat lain.
2. **Buat ruang** (pribadi atau grup): pembuat membuat kunci ruang AES-256-GCM acak, lalu membungkusnya untuk tiap anggota (ECDH dengan kunci sementara + kunci publik anggota). Hasilnya disimpan di `chat_room_keys` (satu baris per anggota per versi kunci).
3. **Kirim pesan**: client mengenkripsi dengan kunci ruang + IV acak baru → kirim `ciphertext`, `iv`, `key_version`.
4. **Baca pesan**: client mengambil `chat_room_keys` miliknya, membuka bungkusan dengan kunci privat, lalu mendekripsi pesan.
5. **Anggota grup keluar / dikeluarkan**: `chat_rooms.current_key_version` naik 1, client yang melakukan aksi membuat kunci ruang baru dan membungkusnya untuk anggota yang tersisa. Anggota lama tidak bisa membaca pesan baru.
6. **Anggota baru masuk**: hanya diberi kunci versi terbaru, jadi tidak bisa membaca pesan lama.

## Yang masih terlihat oleh server (metadata)
Siapa chat dengan siapa, kapan, ukuran pesan, nama grup, dan keanggotaan. Isi pesan tidak terlihat.

## Konsekuensi E2EE
- Server tidak bisa mencari isi pesan (search harus di client).
- Jika user kehilangan kunci privat dan tidak punya cadangan, riwayat chat tidak bisa dipulihkan.
- Laporan/moderasi (`reports`) tidak bisa memeriksa isi chat, kecuali pelapor mengirim plaintext pesan itu secara sukarela.

## Aturan service dan database
- Ruang `PRIVATE` harus punya tepat 2 anggota.
- Hanya anggota aktif (`left_at IS NULL`) yang boleh mengirim dan membaca pesan.
- Hanya OWNER/ADMIN yang boleh menambah atau mengeluarkan anggota grup.
- IV AES-GCM (12 byte acak) hanya boleh dipakai sekali untuk satu ruang dan versi kunci. `chat_message_nonces` menyimpan semua IV yang diterima, termasuk sebelum pesan diedit atau dihapus; constraint unik mencegah pemakaian ulang.
- Kirim dan edit pesan wajib memakai `chat_rooms.current_key_version` dan tidak boleh berada di bawah `min_message_key_version`. Pesan dari versi lama tetap bisa dibaca, tetapi tidak bisa diedit.
- Migrasi V4 mempertahankan nonce yang masih tersedia. Bila pesan pada versi aktif pernah diedit atau dihapus tanpa menyisakan IV, riwayat nonce tidak lengkap: anggota harus merotasi kunci ruang melalui API sebelum mengirim lagi, dengan kunci AES baru, bukan membungkus ulang kunci lama.
- Sebelum menjalankan V4, hentikan semua instance backend lama dan tunggu seluruh transaksi chat selesai. Setelah migrasi, jalankan hanya backend baru yang mencatat nonce. Jangan melakukan rolling deployment atau menjalankan backend lama bersama backend baru: kiriman dari backend lama dapat lolos dari pencatatan nonce.

## Query penting

### 1. Cari atau buat chat pribadi (anti duplikat)
```sql
-- :key = directKeyOf(userA, userB), uuid terkecil lebih dulu
INSERT INTO chat_rooms (type, direct_key, created_by)
VALUES ('PRIVATE', :key, :me)
ON CONFLICT (direct_key) DO NOTHING;

SELECT id FROM chat_rooms WHERE direct_key = :key;
```

### 2. Daftar ruang + pesan terakhir + jumlah belum dibaca
```sql
SELECT r.id, r.type, r.name, r.avatar_path,
       lm.id         AS last_message_id,
       lm.created_at AS last_message_at,
       (SELECT count(*)
          FROM chat_messages m
         WHERE m.room_id = r.id
           AND m.sender_id <> :me
           AND m.deleted_at IS NULL
           AND m.created_at > COALESCE(cm.last_read_at, '-infinity')) AS unread_count
FROM chat_members cm
JOIN chat_rooms r ON r.id = cm.room_id
LEFT JOIN LATERAL (
    SELECT id, created_at FROM chat_messages
     WHERE room_id = r.id
     ORDER BY created_at DESC, id DESC LIMIT 1
) lm ON TRUE
WHERE cm.user_id = :me AND cm.left_at IS NULL
ORDER BY COALESCE(lm.created_at, r.created_at) DESC;
```

### 3. Riwayat pesan (paginasi keyset, 50 per halaman)
```sql
SELECT m.*
FROM chat_messages m
WHERE m.room_id = :room
  AND EXISTS (SELECT 1 FROM chat_members cm
               WHERE cm.room_id = m.room_id AND cm.user_id = :me AND cm.left_at IS NULL)
  AND (:beforeAt IS NULL OR (m.created_at, m.id) < (:beforeAt, :beforeId))
ORDER BY m.created_at DESC, m.id DESC
LIMIT 50;
```

### 4. Ambil kunci ruang milik saya
```sql
SELECT k.key_version, k.ephemeral_public_key, k.iv, k.wrapped_key, k.recipient_key_id
FROM chat_room_keys k
WHERE k.room_id = :room AND k.user_id = :me
ORDER BY k.key_version;
```

### 5. Kunci publik semua anggota (untuk membungkus kunci ruang)
```sql
SELECT cm.user_id, uk.id AS key_id, uk.public_key
FROM chat_members cm
JOIN user_keys uk ON uk.user_id = cm.user_id AND uk.is_active
WHERE cm.room_id = :room AND cm.left_at IS NULL;
```

### 6. Tandai sudah dibaca
```sql
UPDATE chat_members SET last_read_at = now()
WHERE room_id = :room AND user_id = :me;
```

### 7. Keluarkan anggota + rotasi kunci
```sql
UPDATE chat_members SET left_at = now()
WHERE room_id = :room AND user_id = :target AND left_at IS NULL;

UPDATE chat_rooms SET current_key_version = current_key_version + 1, updated_at = now()
WHERE id = :room
RETURNING current_key_version;
-- lalu client menyimpan kunci baru untuk anggota tersisa ke chat_room_keys
```

### 8. Hapus pesan untuk semua orang (hanya pengirim)
```sql
UPDATE chat_messages
   SET deleted_at = now(), ciphertext = NULL, iv = NULL, attachment_path = NULL
 WHERE id = :msg AND sender_id = :me AND deleted_at IS NULL;
```

### 9. Ganti kunci user (rotasi kunci pribadi)
```sql
UPDATE user_keys SET is_active = FALSE, revoked_at = now()
WHERE user_id = :me AND is_active;
INSERT INTO user_keys (user_id, public_key) VALUES (:me, :newPublicKey);
```
