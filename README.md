# GAKBISA — backend & frontend terpisah

```
gakbisa/
├─ backend/            Spring Boot, API murni (JSON + JWT). Tidak lagi menyajikan HTML.
├─ frontend/           Aplikasi web statis (HTML + ES modules). Memanggil backend lewat REST.
├─ docs/API.md         Kontrak REST API lengkap
├─ docs/laporan/       Proposal proyek
└─ legacy-thymeleaf/   Arsip tampilan lama (Thymeleaf + PageController) sebagai bahan porting
```

## Menjalankan

**Backend** (Java 25, seperti sebelumnya)
```bash
cd backend
cp .env.example .env      # isi DB_*, dan JWT_SECRET (min. 32 karakter: openssl rand -base64 48)
./mvnw spring-boot:run    # http://localhost:8080  — cek: /api/health
```

**Frontend** — tidak perlu build/npm. Cukup sajikan foldernya:
```bash
cd frontend
python -m http.server 5500   # atau ekstensi "Live Server" di VS Code → http://localhost:5500
```
Alamat backend diatur di `frontend/js/config.js` (`API_BASE`). Origin frontend harus terdaftar di
`CORS_ALLOWED_ORIGINS` pada `backend/.env` (default sudah mencakup port 5173, 3000, 5500).

## Status porting halaman (Thymeleaf → frontend baru)

| Halaman lama | Status | Endpoint |
|---|---|---|
| login, register | ✅ `pages/login.js`, `register.js` | `/api/auth/login`, `/api/register` |
| dashboard | ✅ `pages/dashboard.js` | `/api/dashboard` |
| barter (daftar, terima/tolak/batal) | ✅ `pages/barter.js` | `/api/barter/*` |
| sessions list + detail (ubah jadwal, selesai, batal, ulasan) | ✅ `pages/sessions.js`, `session-detail.js` | `/api/sessions/*`, `/api/reviews` |
| notifications | ✅ `pages/notifications.js` | `/api/notifications/*` |
| profile (lihat/ubah) | ✅ `pages/profile.js` | `/api/me`, `/api/users/{id}` |
| catalog | ⬜ | `/api/skills`, `/api/categories`, `/api/portfolios` |
| matching | ⬜ | `/api/matching/candidates` |
| barter/request (form kirim barter) | ⬜ | `POST /api/barter` |
| sessions/create (jadwalkan dari barter ACCEPTED) | ⬜ | `POST /api/sessions` |
| profile/skills | ⬜ | `/api/me/skills` |
| profil publik `/profile/{id}` | ⬜ | `/api/users/{id}`, `/api/reviews/user/{id}` |
| portfolio (galeri, upload, edit, hapus) | ⬜ | `/api/portfolios/*` |
| admin (dashboard, users, reports, categories) | ⬜ | `/api/admin/*` |
| embed Jitsi di halaman (`jitsi-room.js`) | ⬜ | `/api/sessions/{id}/meeting` |

Cara menambah halaman: buat `frontend/js/pages/<nama>.js` yang mengekspor `async function(view, params)`
lalu daftarkan satu baris `route("/<path>", handler)` di `frontend/js/main.js`. Salin pola dari `barter.js`.
Tampilan lama ada di `legacy-thymeleaf/src/main/templates/` (class CSS `gk-*` sama dan sudah dimuat).

## Catatan
- Merge conflict di ±43 file telah diselesaikan dengan mengambil sisi `origin/main`.
- `legacy-thymeleaf/gakbisa-controller-stale/` adalah folder lama berpackage `com.gakbisa` (bukan `com.gakbisa.backend`)
  yang tidak ikut dikompilasi; disimpan hanya sebagai arsip.
