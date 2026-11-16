import { register } from "../auth.js";
import { navigate } from "../router.js";
import { authLayout, authField, bindAuthForm } from "../auth-layout.js";

export default function page(view) {
  view.innerHTML = authLayout({
    eyebrow: "MULAI DARI SINI",
    title: "Daftar akun mahasiswa.",
    subtitle: "Buat akun, pilih keahlianmu, lalu temukan partner belajar.",
    body: `
      <form id="f" class="ga-fields">
        ${authField({ label: "Nama lengkap", name: "fullName", attrs: 'required maxlength="120" autocomplete="name"' })}
        ${authField({ label: "Email", name: "email", type: "email", placeholder: "nama@kampus.ac.id", attrs: 'required autocomplete="username"' })}
        ${authField({ label: "Kampus (opsional)", name: "campus", attrs: 'maxlength="150"' })}
        ${authField({ label: "Password (8–72 karakter)", name: "password", type: "password", attrs: 'required minlength="8" maxlength="72" autocomplete="new-password"' })}
        <button class="ga-submit">Daftar sekarang <i class="bi bi-arrow-right"></i></button>
        <p class="ga-alt">Sudah punya akun? <a href="#/login">Masuk</a></p>
      </form>`,
  });
  bindAuthForm(view, async (d) => {
    await register(Object.fromEntries(d));
    navigate("/dashboard");
  }, "Mendaftarkan…");
}
