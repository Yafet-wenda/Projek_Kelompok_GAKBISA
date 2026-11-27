# GAKBISA REST API

Base URL (dev): `http://localhost:8080`  ·  Format: JSON  ·  Auth: JWT Bearer

## Autentikasi

1. `POST /api/auth/login` dengan `{"email": "...", "password": "..."}`
2. Respons: `{"accessToken": "...", "tokenType": "Bearer", "expiresIn": 7200, "user": {...}}`
3. Kirim di setiap request berikutnya: `Authorization: Bearer <accessToken>`
4. Token kedaluwarsa (default 120 menit) → API menjawab **401**; frontend menghapus token dan kembali ke halaman login.
   Akun yang ditangguhkan admin langsung ditolak (401) walau token belum kedaluwarsa, karena akun dicek ulang di tiap request.

Tanpa session & tanpa cookie, jadi tidak ada CSRF. Logout cukup menghapus token di sisi klien.

## Format error

| Status | Kapan | Bentuk |
|---|---|---|
| 400 | Data tidak valid / aturan bisnis dilanggar | `{"title":"Data tidak valid","detail":"...","status":400,"timestamp":"..."}` |
| 401 | Belum login / token salah / kedaluwarsa / password salah | `{"status":401,"error":"Unauthorized","message":"..."}` atau ProblemDetail (login) |
| 403 | Bukan pemilik/peserta, bukan admin, akun ditangguhkan | ProblemDetail atau `{"status":403,...}` |
| 404 | Data tidak ada | ProblemDetail |

Tampilkan `detail` (atau `message`) ke pengguna.

## Endpoint

🔓 = publik (tanpa token) · 🛡 = khusus ADMIN · 🆕 = ditambahkan untuk pemisahan frontend/backend

### Akun
| Method | Path | Body / Query | Keterangan |
|---|---|---|---|
| POST 🔓🆕 | `/api/auth/login` | `{email, password}` | Dapatkan JWT |
| POST 🔓 | `/api/register` | `{fullName, email, password(8-72), campus?}` | Daftar akun STUDENT; admin ditetapkan oleh operator |
| POST | `/api/supabase/signup?email=&password=` | query `email`, `password` | Buat akun melalui Supabase Auth; sesuai konfigurasi keamanan saat ini endpoint memerlukan Bearer token |
| POST | `/api/supabase/signin?email=&password=` | query `email`, `password` | Masuk melalui Supabase Auth dan mengembalikan token; sesuai konfigurasi keamanan saat ini endpoint memerlukan Bearer token |
| GET | `/api/me` | | Data akun sendiri |
| PUT 🆕 | `/api/me` | `{fullName, bio, campus}` | Ubah profil sendiri |
| GET 🔓🆕 | `/api/health` | | `{"status":"ok"}` |

### Katalog, keahlian, matching
| Method | Path | Keterangan |
|---|---|---|
| GET 🔓 | `/api/skills?q=&categoryId=&page=0&size=20` | Katalog berhalaman; kata kunci dan kategori dapat dipakai bersama |
| GET 🔓 | `/api/categories` | Daftar kategori |
| GET 🔓 | `/api/users/{id}` | Profil publik: identitas, reputasi, `completedSessionCount`, dan `offers[]` |
| GET | `/api/me/skills?type=OFFER\|WANT` | Keahlian milik sendiri |
| POST | `/api/me/skills` | `{skillId \| skillName, type, level, notes}` |
| PUT | `/api/me/skills/{id}` | `{level, notes}` — ubah level dan catatan |
| DELETE | `/api/me/skills/{id}` | Hapus keahlian |
| GET | `/api/matching/candidates` | Kandidat partner berskor |
| GET | `/api/matching/search?keyword=&categoryId=&level=&campus=&studyProgram=&page=0&size=20` | Cari kandidat dengan filter gabungan; hasil berhalaman dan tetap memuat alasan kecocokan |

### Barter
| Method | Path | Body | Keterangan |
|---|---|---|---|
| POST | `/api/barter` | `BarterCreateRequest` | Kirim permintaan barter |
| GET | `/api/barter/inbox` · `/outbox` | | Permintaan masuk / terkirim |
| GET 🆕 | `/api/barter/{id}` | | Detail (hanya kedua pihak) |
| POST | `/api/barter/{id}/respond` | `{"accept": true\|false}` | Penerima menerima/menolak |
| POST | `/api/barter/{id}/cancel` | | Pengirim membatalkan |
| GET | `/api/barter/{id}/agreement` | | Lihat usulan dan persetujuan kedua pihak |
| POST | `/api/barter/{id}/agreement` | `{"durationMinutes":60,"sessionCount":4}` | Usulkan / ubah total durasi dan jumlah sesi saat tak ada sesi aktif |
| POST | `/api/barter/{id}/agreement/accept` | | Setujui usulan; kedua pihak harus setuju sebelum sesi dijadwalkan |
| POST | `/api/barter/{barterId}/close` | — | Ajukan penutupan; respons `{confirmations, status}`. Barter selesai setelah kedua pihak mengonfirmasi |
| GET/POST | `/api/barter/{barterId}/goals` | POST: `{"description":"..."}` | Lihat / tambah target belajar (maks. 500 karakter); hanya barter ACCEPTED yang dapat ditambah target |
| PATCH | `/api/barter/{barterId}/goals/{goalId}` | `{"done":true}` | Perbarui status target; barter otomatis selesai saat semua target ada dan selesai |
| GET | `/api/barter/{barterId}/chat` | — | Daftar pesan; peserta barter ACCEPTED/COMPLETED |
| POST | `/api/barter/{barterId}/chat` | `{"message":"..."}` | Kirim pesan (maks. 2000 karakter) |
| DELETE | `/api/barter/{barterId}/chat/{messageId}` | — | Hapus pesan milik sendiri |
| POST | `/api/barter/{barterId}/chat/{messageId}/report` | `{"reason":"..."}` | Laporkan pesan partner; alasan 5–500 karakter |

`BarterDto`: `{id, requesterId, requesterName, targetUserId, targetUserName, offeredSkill, wantedSkill, message, status, createdAt, updatedAt}`

### Sesi belajar & jadwal
| Method | Path | Body / Query | Keterangan |
|---|---|---|---|
| POST | `/api/sessions` | `{barterRequestId, scheduledAt(ISO), durationMinutes, externalMeetingUrl?}` | Jadwalkan dengan durasi/jumlah sesuai kesepakatan yang telah disetujui |
| GET | `/api/sessions` | | Sesi milik saya |
| GET | `/api/sessions/{id}` | | Detail (peserta saja) |
| PATCH | `/api/sessions/{id}?at=<ISO>&durationMinutes=60` | query | Usulkan perubahan jadwal sesi SCHEDULED; partner perlu menerima proposal |
| POST | `/api/sessions/{id}/schedule/confirm` | — | Terima usulan jadwal awal |
| POST | `/api/sessions/{id}/schedule/reject` | — | Tolak usulan jadwal awal |
| POST | `/api/sessions/{id}/reschedule/confirm` | — | Terima usulan jadwal ulang |
| POST | `/api/sessions/{id}/reschedule/reject` | — | Tolak usulan jadwal ulang atau menarik usulan sendiri |
| POST | `/api/sessions/{id}/follow-up` | `{scheduledAt, durationMinutes}` | Usulkan sesi lanjutan dari sesi barter yang selesai dan masih memiliki target terbuka atau permintaan tindak lanjut |
| POST | `/api/sessions/{id}/status` | `{"status":"ONGOING\|COMPLETED\|CANCELLED"}` | Ubah status / batalkan |
| GET | `/api/sessions/{id}/meeting` | | Data meeting (Jitsi/JaaS) |
| GET/POST | `/api/sessions/{sessionId}/chat` | POST: `{"message":"..."}` | Baca / kirim pesan sesi barter |
| DELETE | `/api/sessions/{sessionId}/chat/{messageId}` | — | Hapus pesan milik sendiri |
| POST | `/api/sessions/{sessionId}/chat/{messageId}/report` | `{"reason":"..."}` | Laporkan pesan partner; alasan 5–500 karakter |
| GET | `/api/sessions/{sessionId}/history` | — | Riwayat perubahan jadwal sesi |
| GET/POST | `/api/sessions/{sessionId}/attendance` | POST tanpa body | Lihat / konfirmasi kehadiran sendiri saat check-in dibuka |
| GET/POST | `/api/sessions/{sessionId}/feedback` | POST: `{achieved, remaining?, needsFollowup}` | Lihat / kirim umpan balik sesi COMPLETED; achieved wajib, masing-masing teks maks. 2000 karakter |

`SessionDto`: `{id, barterRequestId?, groupId?, followupOfId?, requesterId?, targetUserId?, scheduledAt, durationMinutes, meetingProvider, meetingRoom, meetingUrl, status, completedAt}`

### Ulasan
| Method | Path | Keterangan |
|---|---|---|
| POST | `/api/reviews` | `{learningSessionId, rating(1-5), comment?}` |
| GET | `/api/reviews/mine` · `/user/{userId}` · `/user/{userId}/reputation` | |

### Portofolio
| Method | Path | Keterangan |
|---|---|---|
| GET 🔓 | `/api/portfolios?page=0&size=12` | Galeri publik (berhalaman) |
| GET 🔓 | `/api/portfolios/user/{userId}` | Karya publik milik pengguna dan karya pribadi milik sendiri/admin |
| POST | `/api/portfolios` | `multipart/form-data`: `file`, `title`, `description?`, `visibility?` |
| GET 🔓 | `/api/portfolios/{id}` | Detail |
| GET 🔓 | `/api/portfolios/{id}/content` · `/access-url` | Isi/tautan karya publik; karya PRIVATE memerlukan pemilik atau admin |
| PUT 🆕 | `/api/portfolios/{id}` | `{title, description, visibility}` — ubah metadata saja |
| DELETE | `/api/portfolios/{id}` | Hapus |

> Karya publik dapat ditampilkan langsung. Untuk karya PRIVATE, ambil konten dengan `fetch` + `blob()` dan header Authorization.

### Notifikasi & beranda
| Method | Path | Keterangan |
|---|---|---|
| GET | `/api/notifications` | 50 terbaru |
| GET 🆕 | `/api/notifications/unread-count` | `{"count": n}` untuk lencana navbar |
| POST | `/api/notifications/{id}/read` | Tandai satu dibaca |
| POST 🆕 | `/api/notifications/read-all` | `{"updated": n}` |
| GET 🆕 | `/api/dashboard` | `{pendingBarters, activeSessions, unreadNotifications, topMatches[], recentNotifications[]}` |

### Kebutuhan belajar & umpan balik aplikasi
| Method | Path | Body | Keterangan |
|---|---|---|---|
| GET | `/api/learning-needs` | — | Daftar kebutuhan belajar berstatus OPEN |
| POST | `/api/learning-needs` | `{topic, level?, targetDate?, details?}` | Topik maks. 150 karakter, level default BEGINNER, detail maks. 2000 karakter |
| POST | `/api/learning-needs/{id}/close` | — | Tutup kebutuhan milik sendiri |
| POST | `/api/feedback` | `{message}` | Kirim masukan aplikasi (5–2000 karakter) |
| GET 🛡 | `/api/feedback/admin` | — | Daftar maksimal 200 masukan aplikasi terbaru |

### Kelompok belajar
| Method | Path | Keterangan |
|---|---|---|
| GET/POST | `/api/groups` | Daftar kelompok yang dapat dilihat / buat kelompok `{name, skillId, level, maxMembers, visibility}`; daftar memuat penanda `recommended` bila cocok dengan skill WANT |
| POST | `/api/groups/{id}/join` · `/leave` | Gabung / keluar; kelompok terbatas membuat permintaan |
| GET | `/api/groups/{id}/members` | Daftar anggota; hanya anggota aktif |
| POST | `/api/groups/{id}/members/{userId}/approve` | Pemilik menyetujui permintaan |
| POST | `/api/groups/{id}/transfer` · `/close` | `{newOwnerId}` / tutup kelompok |
| GET | `/api/groups/{groupId}/chat` | — | Baca pesan; anggota aktif saja |
| POST | `/api/groups/{groupId}/chat` | `{"message":"..."}` | Kirim pesan teks (maks. 2000 karakter) |
| DELETE | `/api/groups/{groupId}/chat/{messageId}` | — | Hapus pesan milik sendiri |
| POST | `/api/groups/{groupId}/chat/{messageId}/report` | `{"reason":"..."}` | Laporkan pesan anggota lain; alasan 5–500 karakter |
| GET/POST | `/api/groups/{id}/sessions` | Lihat sesi kelompok / pemilik menjadwalkan sesi Jitsi |
| POST | `/api/groups/{id}/sessions/{sessionId}/status` | Pemilik menandai `COMPLETED` atau `CANCELLED` |

### Verifikasi kampus dan sengketa
| Method | Path | Keterangan |
|---|---|---|
| GET/POST | `/api/me/campus-verification` | Lihat status / ajukan `{campusEmail}`; domain diatur melalui `CAMPUS_EMAIL_DOMAINS`, perlu `CAMPUS_MAIL_API_KEY` untuk mengirim kode |
| POST | `/api/me/campus-verification/confirm` | `{"code":"123456"}`; kode berlaku 15 menit |
| GET | `/api/admin/campus-verifications` | Daftar permintaan yang menunggu |
| POST | `/api/admin/campus-verifications/{id}/review` | `{approved: true|false}` |
| POST | `/api/disputes` | `{barterRequestId, reason}`; peserta barter |
| GET | `/api/disputes/admin` | Antrean sengketa admin |
| POST | `/api/disputes/admin/{id}/resolve` | `{resolution, addStrikeToPartner, removeStrikeFromReporter}` |

### Moderasi chat
| Method | Path | Body | Keterangan |
|---|---|---|---|
| GET 🛡 | `/api/admin/chat-reports` | — | Daftar laporan pesan terbuka |
| POST 🛡 | `/api/admin/chat-reports/{id}/review` | `{dismiss: true\|false}` | Tolak laporan (`DISMISSED`) atau tindak lanjuti (`RESOLVED`) |

Pengingat sesi 24 jam dan 1 jam diproses otomatis oleh backend. Setelah masa toleransi, strike no-show diproses untuk sesi barter dua orang; jika tak ada pihak check-in, kedua pihak mendapat strike dan dapat mengajukan sengketa. Admin dapat menghapus strike pelapor setelah memeriksa sengketa.

### Laporan & admin
| Method | Path | Keterangan |
|---|---|---|
| POST | `/api/reports` | `{targetType, targetId, reason}` |
| GET 🛡 | `/api/admin/summary` | Ringkasan |
| GET 🛡 | `/api/admin/users` | Daftar pengguna |
| POST 🛡 | `/api/admin/users/{id}/status` | Suspend / aktifkan |
| GET 🛡 | `/api/admin/reports?status=OPEN` | Antrean moderasi |
| POST 🛡 | `/api/admin/reports/{id}/review` | `{decision, hidePortfolio, suspendUser}` |
| GET 🛡🆕 | `/api/admin/categories` | Kategori + nama keahlian |
| POST 🛡 | `/api/admin/categories` · `/api/admin/skills` | Tambah kategori / keahlian |

## Contoh (curl)

```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@kampus.ac.id","password":"rahasia123"}' | python -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
curl -s localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH "localhost:8080/api/sessions/<id>?at=2026-12-01T03:00:00Z&durationMinutes=60" -H "Authorization: Bearer $TOKEN"
```
