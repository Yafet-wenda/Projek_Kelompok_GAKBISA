import { register } from "../auth.js";
import { navigate } from "../router.js";
import { alertBox } from "../ui.js";

export default function page(view) {
  view.innerHTML = `
    <div class="container py-5" style="max-width:420px">
      <div class="text-center mb-4"><h1 class="h4 fw-bold">Daftar akun mahasiswa</h1></div>
      <div id="msg"></div>
      <form id="f" class="gk-card p-4 d-flex flex-column gap-3">
        <div><label class="form-label small fw-bold">Nama lengkap</label>
          <input name="fullName" class="form-control" required maxlength="120"></div>
        <div><label class="form-label small fw-bold">Email</label>
          <input name="email" type="email" class="form-control" required></div>
        <div><label class="form-label small fw-bold">Kampus (opsional)</label>
          <input name="campus" class="form-control" maxlength="150"></div>
        <div><label class="form-label small fw-bold">Password (8–72 karakter)</label>
          <input name="password" type="password" class="form-control" required minlength="8" maxlength="72"></div>
        <button class="gk-btn text-white" style="background:var(--gk-primary)">Daftar</button>
        <div class="text-center small">Sudah punya akun? <a href="#/login">Masuk</a></div>
      </form>
    </div>`;
  view.querySelector("#f").onsubmit = async (ev) => {
    ev.preventDefault();
    const d = Object.fromEntries(new FormData(ev.target));
    try {
      await register(d);
      navigate("/dashboard");
    } catch (e) {
      view.querySelector("#msg").innerHTML = alertBox(e.message);
    }
  };
}
