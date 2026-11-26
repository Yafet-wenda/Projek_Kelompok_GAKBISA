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
| POST 🔓 | `/api/register` | `{fullName, email, password(8-72), campus?}` | Daftar akun STUDENT (termasuk akun pertama di DB kosong) |
| GET | `/api/me` | | Data akun sendiri |
| PUT 🆕 | `/api/me` | `{fullName, bio, campus}` | Ubah profil sendiri |
| GET 🔓🆕 | `/api/health` | | `{"status":"ok"}` |

### Katalog, keahlian, matching
| Method | Path | Keterangan |
|---|---|---|
| GET 🔓 | `/api/skills` | Daftar keahlian |
| GET 🔓 | `/api/categories` | Daftar kategori |
| GET 🔓 | `/api/users/{id}` | Profil publik: `{id, fullName, campus, averageRating, reviewCount, portfolioCount, offers[]}` |
| GET | `/api/me/skills?type=OFFER\|WANT` | Keahlian milik sendiri |
| POST | `/api/me/skills` | `{skillId \| skillName, type, level, notes}` |
| DELETE | `/api/me/skills/{id}` | Hapus keahlian |
| GET | `/api/matching/candidates` | Kandidat partner berskor |

### Barter
| Method | Path | Body | Keterangan |
|---|---|---|---|
| POST | `/api/barter` | `BarterCreateRequest` | Kirim permintaan barter |
| GET | `/api/barter/inbox` · `/outbox` | | Permintaan masuk / terkirim |
| GET 🆕 | `/api/barter/{id}` | | Detail (hanya kedua pihak) |
| POST | `/api/barter/{id}/respond` | `{"accept": true\|false}` | Penerima menerima/menolak |
| POST | `/api/barter/{id}/cancel` | | Pengirim membatalkan |

`BarterDto`: `{id, requesterId, requesterName, targetUserId, targetUserName, offeredSkill, wantedSkill, message, status, createdAt, updatedAt}`

### Sesi belajar & jadwal
| Method | Path | Body / Query | Keterangan |
|---|---|---|---|
| POST | `/api/sessions` | `{barterRequestId, scheduledAt(ISO), durationMinutes(15-240), externalMeetingUrl?}` | Jadwalkan (barter harus ACCEPTED) |
| GET | `/api/sessions` | | Sesi milik saya |
| GET | `/api/sessions/{id}` | | Detail (peserta saja) |
| PATCH | `/api/sessions/{id}?at=<ISO>&durationMinutes=60` | query | **Ubah jadwal** (hanya sesi SCHEDULED) |
| POST | `/api/sessions/{id}/status` | `{"status":"ONGOING\|COMPLETED\|CANCELLED"}` | Ubah status / batalkan |
| GET | `/api/sessions/{id}/meeting` | | Data meeting (Jitsi/JaaS) |

`SessionDto`: `{id, barterRequestId, requesterId, targetUserId, scheduledAt, durationMinutes, meetingProvider, meetingRoom, meetingUrl, status, completedAt}`

### Ulasan
| Method | Path | Keterangan |
|---|---|---|
| POST | `/api/reviews` | `{learningSessionId, rating(1-5), comment?}` |
| GET | `/api/reviews/mine` · `/user/{userId}` · `/user/{userId}/reputation` | |

### Portofolio
| Method | Path | Keterangan |
|---|---|---|
| GET 🔓 | `/api/portfolios?page=0&size=12` | Galeri publik (berhalaman) |
| GET | `/api/portfolios/user/{userId}` | Karya milik seorang pengguna |
| POST | `/api/portfolios` | `multipart/form-data`: `file`, `title`, `description?`, `visibility?` |
| GET 🔓 | `/api/portfolios/{id}` | Detail |
| GET | `/api/portfolios/{id}/content` · `/access-url` | Isi berkas / tautan akses |
| PUT 🆕 | `/api/portfolios/{id}` | `{title, description, visibility}` — ubah metadata saja |
| DELETE | `/api/portfolios/{id}` | Hapus |

> Gambar dari `/content` butuh header Authorization, jadi tidak bisa dipakai langsung di `<img src>`. Ambil dengan `fetch` + `blob()`, atau pakai `/access-url`.

### Notifikasi & beranda
| Method | Path | Keterangan |
|---|---|---|
| GET | `/api/notifications` | 50 terbaru |
| GET 🆕 | `/api/notifications/unread-count` | `{"count": n}` untuk lencana navbar |
| POST | `/api/notifications/{id}/read` | Tandai satu dibaca |
| POST 🆕 | `/api/notifications/read-all` | `{"updated": n}` |
| GET 🆕 | `/api/dashboard` | `{pendingBarters, activeSessions, unreadNotifications, topMatches[], recentNotifications[]}` |

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

### Chat terenkripsi

Endpoint `/api/chat/**` menggunakan JWT Bearer yang sama dengan endpoint API lainnya, bukan session cookie/CSRF. Klien mengenkripsi pesan dan lampiran (maksimal 1 MB) di browser, mengelola anggota grup dengan rotasi kunci, dan memeriksa percakapan berkala; detail format dan batasan ada di [panduan Chat E2EE](../backend/docs/chat-api.md).

## Contoh (curl)

```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@kampus.ac.id","password":"rahasia123"}' | python -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
curl -s localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
curl -s -X PATCH "localhost:8080/api/sessions/<id>?at=2026-12-01T03:00:00Z&durationMinutes=60" -H "Authorization: Bearer $TOKEN"
```
