# GAKBISA Backend (Spring Boot)

Backend aplikasi pertukaran keahlian mahasiswa **GAKBISA** — Java 25 LTS + Spring Boot 4.1.x,
PostgreSQL (Supabase), Thymeleaf (dipakai tim frontend), Spring Security + BCrypt,
Supabase Storage untuk portofolio, Jitsi Meet untuk sesi belajar.

Cakupan repo ini: bagian **Yafet Wenda** (Analisis, Backend & Security) sesuai proposal kelompok.

## Prasyarat mesin (Windows)

1. **JDK 25 (LTS)** — unduh installer Windows x64 dari salah satu:
   - Adoptium Temurin 25: https://adoptium.net/temurin/releases/
   - Oracle Java 25: https://www.oracle.com/java/technologies/downloads/
   Jalankan installer, centang *"Set JAVA_HOME"* / *"Add to PATH"*.
2. **Maven tidak wajib** — proyek ini memakai Maven Wrapper (`mvnw.cmd`) yang mengunduh Maven sendiri.

### Langkah verifikasi (buka terminal baru setelah install)

```bash
java -version    # harus muncul: openjdk version "25"
cd "F:/wenda/Projek GAKBISA/backend"
./mvnw.cmd -q -v # wrapper bekerja (butuh internet saat pertama jalan)
```

## Menjalankan

Satu perintah, dari terminal VS Code apa pun (posisinya di folder proyek, bukan di `backend`):

```powershell
.\run.cmd            # PostgreSQL lokal (backend\.env); cluster dev ikut dinyalakan
.\run.cmd supa       # database Supabase milik Nando (backend\.env.supabase)
```

`run.cmd` hanya memanggil `backend\start.ps1`, dan script itu menjalankan
`mvnw.cmd spring-boot:run` seperti biasa. Berhenti dengan `Ctrl+C`.
Kalau lebih suka tombol, Spring Boot Dashboard di VS Code juga bisa: klik play pada
`gakbisa-backend`.

Setup sekali di mesin baru:

```powershell
cd "F:\wenda\Projek GAKBISA\backend"
cp .env.example .env   # lalu isi kredensial database
```

### Database lokal (Opsi B — dipakai sementara)

Database produksi Supabase diurusi Haudajun Nando. Sambil menunggu skemanya siap,
backend jalan di PostgreSQL lokal. Setup sekali (bikin database `gakbisa` + simpan
password ke `.env`, password diketik di prompt jadi tidak lewat chat):

```powershell
cd "F:\wenda\Projek GAKBISA\backend"
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-local-db.ps1
```

Tabel dibuat otomatis oleh Flyway saat aplikasi start (`V1__init_schema.sql`).
Script `setup-local-db.ps1` di atas mengasumsikan PostgreSQL di port **5432**; cluster dev
yang dipakai sekarang ada di 5433, jadi jalankan script itu hanya saat menyiapkan mesin baru.

Pindah ke database Supabase milik Nando tinggal isi `backend\.env.supabase` (berkas ini tidak
ikut di-commit) lalu `.\run.cmd supa`. Berkas `.env` lokal dibiarkan utuh sebagai cadangan
kalau sedang tidak ada internet.

Upload portofolio baru berfungsi kalau `SUPABASE_URL` + `SUPABASE_SERVICE_KEY` terisi;
kalau kosong, aplikasi tetap jalan dan hanya fitur itu yang menampilkan pesan
"ruang penyimpanan berkas belum aktif".

Status verifikasi (26-10-2026, PostgreSQL lokal port 5433): `mvnw test` → **16/16 lulus**,
`mvnw package` → jar terbentuk, aplikasi start (Tomcat 8080, halaman `/`, `/login`, `/register`,
`/catalog` respons 200, `/dashboard` redirect ke `/login`), dan alur lengkap lewat HTTP
(registrasi → matching → barter → sesi Jitsi → ulasan → notifikasi → admin) **48/48 pemeriksaan lulus**.
Semua halaman Thymeleaf + JS-nya sudah dibuka di browser: nol error konsol, ketujuh tab dasbor
dan panel admin terisi data asli.
Tipe kolom di `V1__init_schema.sql` sudah dicocokkan dengan DDL yang dihasilkan Hibernate,
jadi `ddl-auto: validate` aman.

> **Catatan integrasi:** token CSRF berganti setelah login (session fixation protection).
> Frontend/Postman wajib membaca ulang token dari halaman sesudah login sebelum mengirim POST;
> kalau tidak, responsnya 403. Detail + contoh curl ada di `docs/api-examples.md`.

Aplikasi jalan di **http://localhost:8080** (sengaja bukan port 8000 agar tidak bentrok).

Database start dalam keadaan kosong. Aturan pengisiannya: pendaftar **pertama** otomatis
menjadi ADMIN (akun berikutnya STUDENT), dan keahlian yang diketik pengguna di dasbor langsung
masuk katalog di kategori `Lainnya` — admin yang memindahkannya ke kategori yang tepat.

## Konfigurasi environment (`.env`)

| Variabel | Contoh | Fungsi |
|---|---|---|
| `DB_HOST` | `localhost` / `aws-0-ap-southeast-1.pooler.supabase.com` | Host PostgreSQL. Untuk Supabase ambil dari Settings → Database → Connection string (pakai **pooler**) |
| `DB_PORT` | `5432` | Port PostgreSQL |
| `DB_NAME` | `gakbisa` / `postgres` | Nama database (`postgres` untuk Supabase) |
| `DB_USERNAME` | `postgres` / `postgres.xxxxx` | User. Untuk Supabase pakai **seluruh** `postgres.PROJECT_REF` |
| `DB_PASSWORD` | — | Password database |
| `DB_SSL_MODE` | `disable` / `require` | `require` wajib untuk Supabase, `disable` untuk lokal |
| `SUPABASE_URL` | `https://xxxx.supabase.co` | Basis URL project |
| `SUPABASE_SERVICE_KEY` | `eyJ...` | Service role key untuk Storage API — **hanya di backend, jangan pernah dikirim ke browser** |
| `STORAGE_BUCKET` | `portfolio-files` | Nama bucket portofolio |

> **Keamanan:** `.env` sudah ada di `.gitignore`. Jangan pernah commit kunci service.

## Struktur kode

```
src/main/java/com/gakbisa/backend/
  config/      konfigurasi Beans
  domain/      entity JPA + enum (10 tabel sesuai ERD proposal)
  repository/  Spring Data JPA repositories
  service/     logika bisnis: auth, profil, keahlian, matching, barter, sesi, ulasan, storage, admin
  controller/  endpoint REST (/api/**) untuk Postman + halaman dasar
  dto/         record request/response + mapper
  security/    Spring Security config, UserDetails, BCrypt
db/migration/  V1__init.sql (DDL kanonik, selaras bagian database/QA)
```

## Peta fitur proposal → kode

| Fitur (proposal §3.2/§4) | Lokasi |
|---|---|
| Registrasi/login, BCrypt, role | `security/SecurityConfig`, `service/AuthService` |
| Profil + keahlian OFFER/WANT | `service/UserService`, `service/UserSkillService` |
| Skill matching berbobot (50/30/10/5/5) + alasan | `service/MatchingService` + unit test |
| Barter request + transisi status valid | `service/BarterService`, `domain/BarterStatus` |
| Sesi belajar + room Jitsi acak | `service/SessionService` (nama ruang dari SecureRandom) |
| Ulasan + reputasi (1 ulasan/sesi) | `service/ReviewService` |
| Notifikasi in-app | `service/NotificationService` |
| Laporan + moderasi admin | `service/ReportService`, `service/AdminService` |
| Upload portofolio ke Supabase Storage (validasi MIME/ukuran 10MB, hapus file yatim) | `service/SupabaseStorageService`, `service/PortfolioService` |
| Pagination & batas hasil pencarian | `SkillService`, `MatchingService` |

## Pengujian

```bash
./mvnw.cmd test
```

Unit test menutup skenario uji proposal §8.2 yang bisa diverifikasi dari backend:
registrasi email duplikat, login salah, transisi status barter, matching satu/dua arah,
sesi sebelum barter diterima, ulasan ganda, akses admin ditolak.

Postman: import koleksi manual dari `docs/api-examples.md` (endpoint REST `/api/**`).

## Catatan untuk frontend (Faiz)

- Halaman Thymeleaf mengonsumsi model dari `PageController`; data juga tersedia sebagai JSON di `/api/**`.
- CSRF aktif: form POST wajib menyertakan `_csrf` (Thymeleaf hidden input). Untuk `fetch`,
  baca dari `<meta name="_csrf">` + `<meta name="_csrf_header">` yang sudah dipasang di
  `templates/fragments/layout.html :: head`, jangan hardcode.
- Token CSRF **berganti setelah login**. Ambil ulang token dari halaman sesudah login,
  kalau tidak semua POST balasannya 403.
- Meeting: `GET /api/sessions/{id}` mengembalikan `meetingUrl` **dan** `meetingRoom`
  hanya untuk peserta sesi — tanamkan via Jitsi IFrame API.
- Status barter ikut berubah sendiri saat sesi terakhir `COMPLETED`; UI tidak perlu kirim status manual.

### Berkas tampilan yang sudah ada

| Berkas | Isi |
|---|---|
| `static/css/gakbisa.css` | design system: token warna, tipografi, kartu, tab, dialog, toast |
| `templates/fragments/layout.html` | fragment `head`, `skip`, `topbar`, `foot` — dipakai semua halaman |
| `static/js/app.js` | `window.Gakbisa`: `api` (fetch + CSRF), `html` (auto-escape), `toast`, label & format tanggal |
| `static/js/home.js` / `catalog.js` / `dashboard.js` / `forms.js` | logika tiap halaman |

`Gakbisa.html` mengembalikan string yang sudah di-escape, jadi aman langsung dipakai untuk
`innerHTML` dan `.map(...).join("")`; nilai biasa tetap di-escape, jadi tidak perlu `esc()` manual.
