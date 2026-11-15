import { api } from "../api.js";
import { esc, fmtDate, toast } from "../ui.js";

export default async function page(view) {
  const list = await api("/api/notifications");
  view.innerHTML = `
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h1 class="h4 fw-bold m-0">Notifikasi</h1>
      <button id="all" class="gk-btn gk-btn--secondary gk-btn--sm">Tandai semua dibaca</button>
    </div>
    ${list.length ? list.map((n) => `
      <div class="gk-card p-3 mb-2 ${n.read ? "" : "border-primary"}" data-id="${n.id}" data-read="${n.read}" style="cursor:pointer">
        <div class="d-flex justify-content-between"><span class="${n.read ? "" : "fw-bold"}">${esc(n.title)}</span>
          <span class="gk-badge">${esc(n.type)}</span></div>
        <div class="small">${esc(n.body)}</div><div class="small text-muted">${fmtDate(n.createdAt)}</div>
      </div>`).join("") : '<p class="text-muted">Tidak ada notifikasi.</p>'}`;

  view.querySelector("#all").onclick = async () => {
    await api("/api/notifications/read-all", { method: "POST" });
    toast("Semua notifikasi ditandai dibaca");
    await page(view);
  };
  view.querySelectorAll("[data-id]").forEach((el) => (el.onclick = async () => {
    if (el.dataset.read === "true") return;
    await api(`/api/notifications/${el.dataset.id}/read`, { method: "POST" });
    await page(view);
  }));
}
