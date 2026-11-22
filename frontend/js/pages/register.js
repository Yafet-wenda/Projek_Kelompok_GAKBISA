import { register } from "../auth.js";
import { navigate } from "../router.js";
import { esc } from "../ui.js";

export default function page(view) {
  document.body.classList.add("gk-auth");
  view.innerHTML = `
    <main class="gk-auth__stage">
      <div class="gk-auth__shell">
        <a class="gk-auth__mark" href="#/dashboard">
          <img src="images/gakbisa-logo.png" alt="" width="30" height="30">
          <span>GAKBISA</span>
        </a>
        <div class="gk-auth__head">
          <h1 class="gk-auth__title">Buat akun</h1>
          <p class="gk-auth__sub">
            Email dipakai untuk masuk. Nama dan kampusmu tampil di profil yang
            dilihat calon partner.
          </p>
        </div>
        <div id="msg" aria-live="polite"></div>
        <form id="f" class="gk-auth__fields">
          <div>
            <label class="gk-auth__label" for="fullName">Nama lengkap</label>
            <input class="gk-auth__input" type="text" id="fullName" name="fullName"
                   autocomplete="name" maxlength="120" required autofocus>
          </div>
          <div>
            <label class="gk-auth__label" for="email">Email</label>
            <input class="gk-auth__input" type="email" id="email" name="email"
                   placeholder="gakbisa@gmail.com" autocomplete="email" maxlength="254" required>
          </div>
          <div>
            <label class="gk-auth__label" for="password">Password</label>
            <div class="gk-auth__pw">
              <input class="gk-auth__input" type="password" id="password" name="password"
                     autocomplete="new-password" minlength="8" maxlength="72" aria-describedby="password-hint" required>
              <button class="gk-auth__toggle" type="button" aria-pressed="false"
                      aria-controls="password" aria-label="Tampilkan password">
                <i class="bi bi-eye" aria-hidden="true"></i>
              </button>
            </div>
            <p id="password-hint" class="gk-auth__hint">Minimal 8 karakter, maksimal 72.</p>
          </div>
          <div>
            <label class="gk-auth__label" for="campus">Kampus / prodi</label>
            <input class="gk-auth__input" type="text" id="campus" name="campus"
                   autocomplete="organization" maxlength="150" aria-describedby="campus-hint">
            <p id="campus-hint" class="gk-auth__hint">Boleh dikosongkan. Kalau diisi, tampil di profilmu.</p>
          </div>
          <button class="gk-auth__submit" type="submit">Daftar</button>
        </form>
        <p class="gk-auth__switch">Sudah pernah daftar? <a href="#/login">Masuk</a>.</p>
      </div>
      <p class="gk-auth__note">&copy; 2026 GAKBISA. Gantian Ajari, Kita Bisa!</p>
    </main>`;

  const form = view.querySelector("#f");
  const submit = form.querySelector(".gk-auth__submit");
  const message = view.querySelector("#msg");
  const password = form.querySelector("#password");
  const toggle = form.querySelector(".gk-auth__toggle");
  toggle.onclick = () => {
    const show = password.type === "password";
    password.type = show ? "text" : "password";
    toggle.setAttribute("aria-pressed", String(show));
    toggle.setAttribute("aria-label", show ? "Sembunyikan password" : "Tampilkan password");
    toggle.querySelector("i").className = "bi " + (show ? "bi-eye-slash" : "bi-eye");
    password.focus();
  };

  form.onsubmit = async (ev) => {
    ev.preventDefault();
    if (submit.disabled) return;
    const data = Object.fromEntries(new FormData(form));
    submit.disabled = true;
    submit.textContent = "Mendaftarkan…";
    submit.setAttribute("aria-busy", "true");
    message.replaceChildren();
    try {
      await register(data);
      navigate("/dashboard");
    } catch (e) {
      message.innerHTML = `<div class="gk-auth__alert gk-auth__alert--error" role="alert">
        <i class="bi bi-exclamation-circle-fill" aria-hidden="true"></i>
        <span>${esc(e.message || "Terjadi kesalahan")}</span>
      </div>`;
      submit.disabled = false;
      submit.textContent = "Daftar";
      submit.removeAttribute("aria-busy");
    }
  };
  form.querySelector("#fullName").focus();
}
