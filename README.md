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
Untuk deploy, isi `meta[name="gakbisa-api-base"]` di `frontend/index.html` dengan alamat backend
HTTPS (tanpa garis miring akhir), atau set `window.GAKBISA_API_BASE` sebelum `js/main.js` dimuat.
Origin frontend harus terdaftar di `CORS_ALLOWED_ORIGINS` pada `backend/.env`.

## Status frontend aktif

| Halaman lama | Status | Endpoint |
|---|---|---|
| login, register, dashboard | ✅ | `/api/auth/*`, `/api/dashboard` |
| barter: kirim, terima/tolak/batal, target belajar, jadwalkan sesi | ✅ | `/api/barter/*`, `/api/sessions` |
| sesi: detail, ubah jadwal, status, chat, ulasan, feedback | ✅ | `/api/sessions/*`, `/api/reviews` |
| notifikasi | ✅ | `/api/notifications/*` |
| profil, skill OFFER/WANT, portofolio | ✅ | `/api/me`, `/api/me/skills`, `/api/portfolios/*` |
| katalog, matching, profil publik | ✅ | `/api/skills`, `/api/matching/*`, `/api/users/*` |
| kelompok: buat, cari, gabung/keluar, persetujuan anggota, chat, sesi Jitsi, alihkan/tutup | ✅ dasar | `/api/groups/*` |
| admin: laporan, pengguna, kategori, ringkasan, sengketa | ✅ | `/api/admin/*`, `/api/disputes/admin/*` |
| Upload portofolio | ✅ gambar/PDF, maks. 10 MB | `/api/portfolios/*` |

Pengingat sesi dan riwayat jadwal sudah diproses backend. Strike otomatis berlaku bagi peserta barter yang tidak check-in setelah masa toleransi berakhir; jika keduanya absen, strike dicatat untuk keduanya dan keduanya dapat mengajukan sengketa. Verifikasi email kampus mengirim kode enam digit yang berlaku 15 menit; fitur ini memerlukan kunci layanan email dan pengirim terverifikasi. Kesepakatan durasi/jumlah sesi harus disetujui kedua pihak sebelum sesi dijadwalkan. Fitur kelompok memberi penanda rekomendasi berdasarkan skill yang dicari pengguna.

Pendaftaran publik selalu membuat akun mahasiswa. Untuk menetapkan admin produksi, buat akun pengelola lebih dulu, lalu operator tepercaya mengubah perannya di database dengan email akun yang sudah dipastikan:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'email-pengelola@kampus.ac.id';
```

Jangan aktifkan data seed demo di produksi. Saat mengaktifkan profil `prod`, isi `JWT_SECRET` dengan rahasia acak minimal 32 karakter.

Frontend aktif adalah aplikasi statis berbasis JavaScript modules; `legacy-thymeleaf/` hanya arsip tampilan lama.

## Catatan
- Merge conflict di ±43 file telah diselesaikan dengan mengambil sisi `origin/main`.
- `legacy-thymeleaf/gakbisa-controller-stale/` adalah folder lama berpackage `com.gakbisa` (bukan `com.gakbisa.backend`)
  yang tidak ikut dikompilasi; disimpan hanya sebagai arsip.
# Campus email verification

Set `CAMPUS_EMAIL_DOMAINS` to the allowed campus domains. Configure `CAMPUS_MAIL_API_KEY` and a verified `CAMPUS_MAIL_FROM` sender to send six-digit ownership codes; they expire after 15 minutes. `CAMPUS_MAIL_API_URL` defaults to the Resend-compatible email API. Without a mail key, email verification requests are rejected and admins cannot approve an unconfirmed address.
