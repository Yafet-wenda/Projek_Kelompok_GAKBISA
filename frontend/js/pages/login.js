import { login } from "../auth.js";
import { navigate } from "../router.js";
import { alertBox } from "../ui.js";

export default function page(view) {
  view.innerHTML = `
    <div class="container py-5" style="max-width:420px">
      <div class="text-center mb-4">
        <span class="gk-navbar__brand-mark" style="width:56px;height:56px;font-size:28px;border-radius:16px" role="img" aria-label="GAKBISA">G</span>
        <h1 class="h4 fw-bold mt-2">Masuk ke GAKBISA</h1>
      </div>
      <div id="msg"></div>
      <form id="f" class="gk-card p-4 d-flex flex-column gap-3">
        <div><label class="form-label small fw-bold">Email</label>
          <input name="email" type="email" class="form-control" required autocomplete="username"></div>
        <div><label class="form-label small fw-bold">Password</label>
          <input name="password" type="password" class="form-control" required autocomplete="current-password"></div>
        <button class="gk-btn text-white" style="background:var(--gk-primary)">Masuk</button>
        <div class="text-center small">Belum punya akun? <a href="#/register">Daftar</a></div>
      </form>
    </div>`;
  view.querySelector("#f").onsubmit = async (ev) => {
    ev.preventDefault();
    const d = new FormData(ev.target);
    try {
      await login(d.get("email"), d.get("password"));
      navigate("/dashboard");
    } catch (e) {
      view.querySelector("#msg").innerHTML = alertBox(e.message);
    }
  };
}