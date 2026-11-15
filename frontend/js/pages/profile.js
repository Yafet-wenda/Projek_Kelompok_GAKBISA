import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, toast, alertBox } from "../ui.js";

export default async function page(view) {
  // Dua request ini tidak saling bergantung, jadi dijalankan bersamaan (bukan berurutan).
  const [me, rep] = await Promise.all([
    api("/api/me"),
    api(`/api/users/${currentUser().id}`).catch(() => null),
  ]);
  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3">Profil saya</h1>
    ${rep ? `<p class="small text-muted">Rating rata-rata: <b>${rep.averageRating == null ? "-" : Number(rep.averageRating).toFixed(1)}</b>
      · ${esc(rep.reviewCount)} ulasan · ${esc(rep.portfolioCount)} karya</p>` : ""}
    <div id="msg"></div>
    <form id="f" class="gk-card p-4 d-flex flex-column gap-3">
      <div><label class="form-label small fw-bold">Email</label><input class="form-control" value="${esc(me.email)}" disabled></div>
      <div><label class="form-label small fw-bold">Nama lengkap</label><input name="fullName" class="form-control" required value="${esc(me.fullName)}"></div>
      <div><label class="form-label small fw-bold">Kampus</label><input name="campus" class="form-control" value="${esc(me.campus ?? "")}"></div>
      <div><label class="form-label small fw-bold">Bio</label><textarea name="bio" rows="3" class="form-control">${esc(me.bio ?? "")}</textarea></div>
      <button class="gk-btn text-white" style="background:var(--gk-primary)">Simpan</button>
    </form>`;
  view.querySelector("#f").onsubmit = async (ev) => {
    ev.preventDefault();
    try {
      await api("/api/me", { method: "PUT", body: Object.fromEntries(new FormData(ev.target)) });
      toast("Profil disimpan");
    } catch (e) { view.querySelector("#msg").innerHTML = alertBox(e.message); }
  };
}