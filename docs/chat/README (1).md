# Fitur Chat E2EE – GAKBISA

Chat pribadi (1-1) dan grup dengan **enkripsi end-to-end**. Isi pesan dienkripsi di client, server hanya menyimpan ciphertext, sehingga hanya anggota ruang yang bisa membacanya (konsepnya mirip WhatsApp).

> **Status:** baru lapisan model dan database. Belum ada repository, service, controller, maupun kode enkripsi di frontend. Migration dan entity belum dijalankan terhadap PostgreSQL, jadi uji dulu dengan menjalankan aplikasi (`ddl-auto: validate` akan memberi error jika ada kolom yang tidak cocok).

## Isi folder

```
chat-module/
├── README.md
├── docs/
│   └── chat-e2ee-design.md          # alur kunci, aturan service, kumpulan query
└── backend/src/main/
    ├── resources/db/migration/
    │   └── V2__chat_e2ee.sql        # 5 tabel baru
    └── java/com/gakbisa/backend/domain/
        ├── UserKey.java
        ├── ChatRoom.java
        ├── ChatMember.java
        ├── ChatRoomKey.java
        ├── ChatMessage.java
        ├── ChatRoomType.java        # PRIVATE, GROUP
        ├── ChatMemberRole.java      # OWNER, ADMIN, MEMBER
        └── ChatMessageType.java     # TEXT, IMAGE, FILE
```

## Cara pasang

1. Salin `V2__chat_e2ee.sql` ke `backend/src/main/resources/db/migration/`.
2. Salin semua file `.java` ke `backend/src/main/java/com/gakbisa/backend/domain/`.
3. Jalankan backend. Flyway akan menjalankan V2 otomatis, lalu Hibernate memvalidasi entity terhadap tabel.

Syarat: skema V1 sudah ada (tabel `users` dan ekstensi `pgcrypto`).

## Skema database

| Tabel | Fungsi |
|---|---|
| `user_keys` | Kunci publik tiap user, plus cadangan kunci privat terenkripsi (opsional). Satu kunci aktif per user. |
| `chat_rooms` | Ruang chat `PRIVATE` atau `GROUP`. `direct_key` yang unik mencegah ruang pribadi ganda. `current_key_version` naik saat kunci dirotasi. |
| `chat_members` | Anggota ruang: role, `left_at`, `last_read_at` (untuk hitung belum dibaca), `muted`. |
| `chat_room_keys` | Kunci ruang (AES-256-GCM) yang dibungkus untuk tiap anggota, per versi kunci. |
| `chat_messages` | Pesan: `ciphertext`, `iv`, `key_version`, tipe pesan, balasan, waktu edit dan hapus. |

Relasi singkat:

```
users 1─* user_keys
users *─* chat_rooms           (lewat chat_members)
chat_rooms 1─* chat_room_keys  (satu baris per anggota per versi kunci)
chat_rooms 1─* chat_messages
chat_messages 0..1─1 chat_messages   (reply_to_id)
```

## Cara kerja enkripsi

1. **Buat kunci user.** Client membuat pasangan kunci ECDH P-256. Kunci publik dikirim ke server, kunci privat disimpan di client.
2. **Buat ruang.** Pembuat membuat kunci ruang AES-256-GCM acak, lalu membungkusnya untuk tiap anggota dengan ECDH (kunci sementara + kunci publik anggota). Hasilnya disimpan di `chat_room_keys`.
3. **Kirim pesan.** Client mengenkripsi dengan kunci ruang dan IV acak baru (12 byte), lalu mengirim `ciphertext`, `iv`, dan `key_version`.
4. **Baca pesan.** Client mengambil kunci terbungkus miliknya, membukanya dengan kunci privat, lalu mendekripsi pesan.
5. **Anggota keluar atau dikeluarkan.** `current_key_version` naik, kunci ruang baru dibuat dan dibungkus hanya untuk anggota yang tersisa.
6. **Anggota baru.** Hanya menerima kunci versi terbaru, jadi tidak bisa membaca pesan lama.

Detail lengkap ada di [`docs/chat-e2ee-design.md`](docs/chat-e2ee-design.md).

## Yang terlihat dan tidak terlihat oleh server

| Terlihat (metadata) | Tidak terlihat |
|---|---|
| Siapa chat dengan siapa, waktu kirim, ukuran pesan, nama grup, keanggotaan | Isi pesan, kunci ruang, kunci privat user |

## Aturan yang harus dijaga di service

SQL tidak bisa memaksa aturan ini, jadi harus dicek di kode service:

- Ruang `PRIVATE` harus punya tepat 2 anggota.
- Hanya anggota aktif (`left_at IS NULL`) yang boleh mengirim dan membaca pesan.
- Hanya `OWNER` atau `ADMIN` yang boleh menambah atau mengeluarkan anggota grup.
- `key_version` pada pesan harus sama dengan `chat_rooms.current_key_version`.
- IV AES-GCM tidak boleh dipakai ulang dengan kunci yang sama.
- Server tidak boleh menyimpan atau me-log plaintext pesan maupun kunci privat.

## Query penting

Ada 9 query siap pakai di `docs/chat-e2ee-design.md`:

1. Cari atau buat chat pribadi (anti duplikat)
2. Daftar ruang + pesan terakhir + jumlah belum dibaca
3. Riwayat pesan dengan paginasi keyset
4. Ambil kunci ruang milik user
5. Kunci publik semua anggota (untuk membungkus kunci ruang)
6. Tandai sudah dibaca
7. Keluarkan anggota + rotasi kunci
8. Hapus pesan untuk semua orang
9. Ganti kunci user

## Batasan

- Pencarian isi pesan hanya bisa dilakukan di client.
- Jika user kehilangan kunci privat dan tidak punya cadangan, riwayat chat tidak bisa dipulihkan.
- Fitur laporan (`reports`) tidak bisa memeriksa isi chat, kecuali pelapor mengirim plaintext pesan secara sukarela.
- Belum ada status terkirim/diterima per pesan, hanya `last_read_at` per anggota.
- Belum ada fitur "hapus untuk saya saja".

## Rencana lanjutan

- Repository, service, dan controller REST (`/api/chat/...`)
- Realtime lewat WebSocket/STOMP
- Modul enkripsi di frontend (Web Crypto API + IndexedDB)
- Lampiran terenkripsi di storage
- Notifikasi pesan baru lewat tabel `notifications` yang sudah ada
