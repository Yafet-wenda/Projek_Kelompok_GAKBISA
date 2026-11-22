import { login } from "../auth.js";
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
          <h1 class="gk-auth__title">Masuk</h1>
        </div>
        <div id="msg" aria-live="polite"></div>
        <form id="f" class="gk-auth__fields">
          <div>
            <label class="gk-auth__label" for="email">Email</label>
            <input class="gk-auth__input" type="email" id="email" name="email"
                   autocomplete="email" placeholder="gakbisa@gmail.com" maxlength="254" required autofocus>
          </div>
          <div>
            <label class="gk-auth__label" for="password">Password</label>
            <div class="gk-auth__pw">
              <input class="gk-auth__input" type="password" id="password" name="password"
                     autocomplete="current-password" maxlength="72" required>
              <button class="gk-auth__toggle" type="button" aria-pressed="false"
                      aria-controls="password" aria-label="Tampilkan password">
                <i class="bi bi-eye" aria-hidden="true"></i>
              </button>
            </div>
          </div>
          <button class="gk-auth__submit" type="submit">Masuk</button>
        </form>
        <p class="gk-auth__switch">Belum punya akun? <a href="#/register">Daftar</a>.</p>
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
    const data = new FormData(form);
    submit.disabled = true;
    submit.textContent = "Memproses…";
    submit.setAttribute("aria-busy", "true");
    message.replaceChildren();
    try {
      await login(data.get("email"), data.get("password"));
      navigate("/dashboard");
    } catch (e) {
      message.innerHTML = `<div class="gk-auth__alert gk-auth__alert--error" role="alert">
        <i class="bi bi-exclamation-circle-fill" aria-hidden="true"></i>
        <span>${esc(e.message || "Terjadi kesalahan")}</span>
      </div>`;
      submit.disabled = false;
      submit.textContent = "Masuk";
      submit.removeAttribute("aria-busy");
    }
  };
  form.querySelector("#email").focus();
}
