import { login } from "../auth.js";
import { navigate } from "../router.js";
import { authLayout, authField, bindAuthForm } from "../auth-layout.js";

export default function page(view) {
  view.innerHTML = authLayout({
    eyebrow: "SELAMAT DATANG KEMBALI",
    title: "Lanjut jadi lebih bisa.",
    subtitle: "Masuk untuk melanjutkan perjalanan belajarmu.",
    body: `
      <form id="f" class="ga-fields">
        ${authField({ label: "Email", name: "email", type: "email", placeholder: "nama@kampus.ac.id", attrs: 'required autocomplete="username"' })}
        ${authField({ label: "Password", name: "password", type: "password", placeholder: "Masukkan password kamu", attrs: 'required autocomplete="current-password"' })}
        <button class="ga-submit">Masuk ke GAKBISA <i class="bi bi-arrow-right"></i></button>
        <p class="ga-alt">Belum punya akun? <a href="#/register">Daftar akun mahasiswa</a></p>
        <p class="ga-note">Gunakan akun pribadimu dan jangan bagikan password kepada siapa pun.</p>
      </form>`,
  });
  bindAuthForm(view, async (d) => {
    await login(d.get("email"), d.get("password"));
    navigate("/dashboard");
  }, "Memproses…");
}
