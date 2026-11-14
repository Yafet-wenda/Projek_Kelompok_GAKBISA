# Catatan Integrasi Controller GAKBISA

## Dependensi pom.xml (Spring Boot 4.1.x, Java 25)
spring-boot-starter-webmvc, -thymeleaf, -validation, -data-jpa, -security,
thymeleaf-extras-springsecurity6, postgresql

## Konfigurasi koneksi Supabase PostgreSQL
Gunakan environment variables Spring Boot berikut (nilai rahasia jangan di-commit):
- SPRING_DATASOURCE_URL=jdbc:postgresql://<host-database-supabase>:5432/postgres?sslmode=require
- SPRING_DATASOURCE_USERNAME=postgres
- SPRING_DATASOURCE_PASSWORD=<password-database-supabase>
- SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver

Catatan: Spring Boot tidak otomatis membaca file .env secara native. Variabel harus diekspor ke environment IDE/terminal atau dimuat oleh konfigurasi yang sudah ada.

## application.properties (potongan yang perlu diterapkan pada konfigurasi yang sudah ada)
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=55MB
spring.jpa.hibernate.ddl-auto=validate

## Database dan Storage
- Database utama: Supabase PostgreSQL. Jangan gunakan lagi FreeSQLDatabase/MySQL.
- File fisik portofolio: Supabase Storage; metadata file disimpan di PostgreSQL.
- SQL pada file `gakbisa_freesql.sql` sudah dikonversi menjadi sintaks PostgreSQL meskipun nama file dipertahankan agar tidak menambah file baru.

## Aturan SecurityConfig yang dibutuhkan controller
- permitAll : /, /login, /register, /catalog, /portfolios/*/file (hak akses dicek di service),
              /css/**, /js/**, /images/**
- hasRole ADMIN : /admin/**
- authenticated : selain di atas
- formLogin: loginPage("/login"), defaultSuccessUrl("/dashboard", true)
- Tambahkan @EnableMethodSecurity (untuk @PreAuthorize di AdminController)
- CSRF tetap aktif; semua <form> POST di Thymeleaf (th:action) otomatis menyertakan token

## Kontrak yang harus diimplementasi
Semua interface di package `service` diimplementasi oleh Service Layer (backend).
Identitas user = `principal.getName()` = email dari login (UserDetails.getUsername()).

## Template Thymeleaf yang dirujuk (nama view)
home, catalog, dashboard, auth/login, auth/register, profile/view, profile/edit,
skills/manage, portfolio/list, matching/list, matching/detail, barter/list, barter/detail,
session/list, session/form, session/detail, session/meeting, notification/list,
admin/dashboard, admin/categories, admin/users, admin/reports, admin/portfolios,
error/400, error/404
