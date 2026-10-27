# Contoh request API GAKBISA (Postman / curl)

Autentikasi memakai **session cookie** (form login). Di Postman:

1. `GET http://localhost:8080/login` (Postman menyimpan cookie & token CSRF otomatis bila "Cookies" aktif).
2. Gunakan **HTTP Requester** bawaan Postman (Auth → Type: *Spring Security HTTP Requester*) agar token CSRF ikut dikirim, atau
3. Salin manual: ambil cookie `JSESSIONID` + token CSRF dari response `/login`, kirim sebagai form field `_csrf`.

Semua endpoint JSON di bawah memakai prefix `/api` dan memerlukan login (kecuali katalog).

## Auth & profil

| Method | Path | Body / catatan |
|---|---|---|
| POST | `/api/register` | `{"fullName":"Yafet","email":"yafet@x.id","password":"rahasia123","campus":"PENS"}` (tanpa login) |
| GET | `/api/me` | profil pengguna login |
| POST | `/login` | form: `username`, `password`, `_csrf` |
| POST | `/logout` | form: `_csrf` |

## Keahlian

| Method | Path | Body |
|---|---|---|
| GET | `/api/skills?q=java&page=0&size=20` | katalog publik, paginated |
| GET | `/api/categories` | daftar kategori |
| GET | `/api/me/skills?type=OFFER` | `type` opsional: OFFER/WANT |
| POST | `/api/me/skills` | `{"skillId":"<uuid>","type":"OFFER","level":"INTERMEDIATE","notes":"opsional"}` |
| DELETE | `/api/me/skills/{userSkillId}` | |

## Matching

`GET /api/matching/candidates?limit=10` →

```json
[{"userId":"...","fullName":"Faiz","campus":"PENS","score":93,
  "reasons":["Kamu mencari UI/UX, dan Faiz menawarkan keahlian itu (level ADVANCED)",
             "Pertukaran dua arah: Faiz mencari Java, dan kamu mengajarkannya"]}]
```

## Barter

| Method | Path | Body |
|---|---|---|
| POST | `/api/barter` | `{"targetUserId":"<uuid>","offeredUserSkillId":"<uuid OFFER milikmu>","wantedUserSkillId":"<uuid WANT milikmu>","message":"halo"}` |
| GET | `/api/barter/inbox` / `/api/barter/outbox` | |
| POST | `/api/barter/{id}/respond` | `{"accept": true}` — hanya penerima |
| POST | `/api/barter/{id}/cancel` | hanya pengirim |

## Sesi & meeting

| Method | Path | Body |
|---|---|---|
| POST | `/api/sessions` | `{"barterRequestId":"<uuid>","scheduledAt":"2026-11-01T10:00:00Z","durationMinutes":60}` |
| GET | `/api/sessions` / `/api/sessions/{id}` | berisi `meetingRoom` + `meetingUrl`, hanya untuk peserta |
| PATCH | `/api/sessions/{id}?at=...&durationMinutes=90` | jadwal ulang |
| POST | `/api/sessions/{id}/status` | `{"status":"ONGOING"}` lalu `{"status":"COMPLETED"}` |

Frontend menanamkan `meetingUrl` ke Jitsi IFrame API (lihat bagian "Ruang meeting" di bawah).

> Ketika sesi terakhir pada sebuah barter ditandai `COMPLETED`, backend otomatis mengubah barter itu menjadi
> `COMPLETED`. Status barter di UI tidak perlu dikirim manual, dan permintaan barter baru ke kandidat yang sama
> langsung bisa dibuat lagi.

## Portofolio

| Method | Path | Body |
|---|---|---|
| GET | `/api/portfolios?page=0&size=12` | galeri publik |
| POST | `/api/portfolios` | multipart: `file` (jpg/png/webp/pdf ≤10MB), `title`, `description?`, `visibility?` (PUBLIC/PRIVATE) |
| GET | `/api/portfolios/{id}/content` | stream dengan cek hak akses |
| GET | `/api/portfolios/{id}/access-url` | signed URL 10 menit utk file privat |
| DELETE | `/api/portfolios/{id}` | pemilik/admin |

## Ulasan & reputasi

| Method | Path | Body |
|---|---|---|
| POST | `/api/reviews` | `{"learningSessionId":"<uuid>","rating":5,"comment":"seru"}` (sesi harus COMPLETED, 1x/org) |
| GET | `/api/reviews/user/{userId}` | daftar ulasan masuk untuk satu pengguna |
| GET | `/api/reviews/user/{userId}/reputation` | `{"averageRating":4.5,"reviewCount":2,"portfolioCount":1}` |
| GET | `/api/reviews/mine` | ulasan yang sudah **saya** tulis — dipakai UI menandai sesi yang sudah dinilai |

## Profil publik

| Method | Path | Isi |
|---|---|---|
| GET | `/api/users/{id}` | `{"id","fullName","campus","averageRating","reviewCount","portfolioCount","offers":[...]}` |

Sengaja **tidak** mengembalikan `email` atau `status` akun. Endpoint ini yang dipakai katalog
dan dasbor untuk menampilkan nama pemilik karya / partner.

### Mana yang boleh diakses tamu

Hanya `GET` pada `/api/skills`, `/api/categories`, `/api/portfolios`, `/api/portfolios/*`,
`/api/users/*` dan `POST /api/register` yang terbuka tanpa login. Method lain pada path yang sama
butuh sesi login + token CSRF, jadi jangan berasumsi `GET /api/portfolios` anonim berarti
`POST /api/portfolios` juga anonim.

## Notifikasi & laporan

| Method | Path | Body |
|---|---|---|
| GET | `/api/notifications` | 50 terakhir |
| POST | `/api/notifications/{id}/read` | |
| POST | `/api/reports` | `{"targetType":"PORTFOLIO","targetId":"<uuid>","reason":"konten tidak pantas"}` |

## Admin (role ADMIN saja)

| Method | Path | Body |
|---|---|---|
| GET | `/api/admin/summary` | ringkasan sistem |
| GET | `/api/admin/reports?status=OPEN` | |
| POST | `/api/admin/reports/{id}/review` | `{"decision":"RESOLVED","hidePortfolio":true,"suspendUser":false}` |
| POST | `/api/admin/categories` | `{"name":"Musik","description":"..."}` |
| POST | `/api/admin/skills` | `{"name":"Gitar","categoryId":"<uuid>"}` |
| GET | `/api/admin/users` | |
| POST | `/api/admin/users/{id}/status` | `{"status":"SUSPENDED"}` |

## curl singkat (dengan cookie jar + CSRF)

```bash
BASE=http://localhost:8080
JAR=cookies.txt
# Halaman /login memuat dua input _csrf (form login Thymeleaf + th:csrfMetaTags) -> pakai head -1.
TOKEN=$(curl -s -c $JAR $BASE/login | grep -o 'name="_csrf" value="[^"]*"' | head -1 | cut -d'"' -f4)
curl -s -b $JAR -c $JAR -o /dev/null -w "login %{http_code}\n" \
  --data-urlencode "username=yafet@gakbisa.test" --data-urlencode "password=yafet123" \
  --data-urlencode "_csrf=$TOKEN" $BASE/login
# PENTING: login mengaktifkan session fixation protection -> token CSRF sebelum login jadi tidak valid.
# Ambil ulang token dari halaman mana pun SETELAH login, sebelum mengirim POST ke /api/**.
TOKEN=$(curl -s -b $JAR -c $JAR $BASE/dashboard | grep -o 'name="_csrf" value="[^"]*"' | head -1 | cut -d'"' -f4)
curl -s -b $JAR -c $JAR -H "X-CSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
  -d '{"targetUserId":"…","offeredUserSkillId":"…","wantedUserSkillId":"…","message":"Ajarin Java dong"}' \
  $BASE/api/barter
curl -s -b $JAR $BASE/api/matching/candidates
```

> Gagal CSRF tampil sebagai **403** (bukan 405) untuk endpoint `/api/**`; untuk halaman HTML diarahkan ke `/access-denied`.
> POST dengan cookie sesi tapi token dari sebelum login = 403. Ini jebakan paling sering saat integrasi frontend/Postman.
> Sumber token saat ini hanya input tersembunyi `name="_csrf"` di dalam form (login/register/dashboard).
> Untuk kode JS yang memanggil `/api/**` dengan `fetch`, tambahkan di `<head>` template:
> `<meta name="_csrf" th:content="${_csrf.token}"/>` lalu kirim lewat header `X-CSRF-TOKEN`.

## Ruang meeting (Jitsi) pada respons sesi

`GET /api/sessions/{id}` dan `POST /api/sessions/{id}/status` mengembalikan:

```json
{
  "id": "…", "barterRequestId": "…", "status": "SCHEDULED",
  "meetingProvider": "JITSI",
  "meetingRoom": "gakbisa-A1PPxtKd…",
  "meetingUrl": "https://meet.jit.si/gakbisa-A1PPxtKd…"
}
```

- `meetingRoom` adalah **nama ruang acak** (awalan `gakbisa-` + 20 karakter dari SecureRandom). Pakai nilai ini sebagai `roomName` pada Jitsi IFrame API; jangan pernah membentuknya di frontend.
- `meetingUrl` sudah lengkap untuk penyedia `JITSI`. Bila barter memakai tautan eksternal, `meetingProvider` = `EXTERNAL_LINK` dan `meetingUrl` adalah tautan yang diinput pengguna, jadi tanam lewat `<a href>` dan bukan IFrame.
- Field ini hanya muncul untuk peserta sesi; pengguna lain mendapat 403.

