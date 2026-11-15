import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, fmtDate, statusBadge, toast, alertBox } from "../ui.js";

export default async function page(view) {
  const [inbox, outbox] = await Promise.all([api("/api/barter/inbox"), api("/api/barter/outbox")]);
  const me = currentUser().id;

  const card = (b) => {
    const incoming = b.targetUserId === me;
    const partner = incoming ? b.requesterName : b.targetUserName;
    const actions = b.status === "PENDING"
      ? (incoming
          ? `<button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-success)" data-act="accept" data-id="${b.id}">Terima</button>
             <button class="gk-btn gk-btn--danger gk-btn--sm" data-act="reject" data-id="${b.id}">Tolak</button>`
          : `<button class="gk-btn gk-btn--secondary gk-btn--sm" data-act="cancel" data-id="${b.id}">Batalkan</button>`)
      : "";
    return `
      <div class="gk-card p-3 mb-2">
        <div class="d-flex justify-content-between flex-wrap gap-2">
          <div><div class="fw-bold">${esc(partner)}</div>
            <div class="small">${incoming ? "Menawarkan" : "Kamu tawarkan"}: <b>${esc(b.offeredSkill)}</b> ⇄ ${incoming ? "meminta" : "kamu minta"}: <b>${esc(b.wantedSkill)}</b></div>
            ${b.message ? `<div class="small text-muted">“${esc(b.message)}”</div>` : ""}
            <div class="small text-muted">${fmtDate(b.createdAt)}</div></div>
          <div class="text-end">${statusBadge(b.status)}<div class="mt-2 d-flex gap-1 justify-content-end">${actions}</div></div>
        </div>
      </div>`;
  };
  const list = (items) => items.length ? items.map(card).join("") : '<p class="text-muted">Belum ada.</p>';

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3">Barter keahlian</h1>
    <div id="msg"></div>
    <h2 class="h6 fw-bold">Masuk</h2>${list(inbox)}
    <h2 class="h6 fw-bold mt-4">Terkirim</h2>${list(outbox)}`;

  view.onclick = async (ev) => {
    const btn = ev.target.closest("button[data-act]");
    if (!btn) return;
    const { act, id } = btn.dataset;
    try {
      if (act === "cancel") await api(`/api/barter/${id}/cancel`, { method: "POST" });
      else await api(`/api/barter/${id}/respond`, { method: "POST", body: { accept: act === "accept" } });
      toast("Berhasil diperbarui");
      await page(view);
    } catch (e) {
      view.querySelector("#msg").innerHTML = alertBox(e.message);
    }
  };
}
