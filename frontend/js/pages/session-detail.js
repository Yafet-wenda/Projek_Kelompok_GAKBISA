import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, fmtDate, statusBadge, toast, alertBox, toLocalInput } from "../ui.js";

export default async function page(view, { id }) {
  const s = await api(`/api/sessions/${id}`);
  const me = currentUser().id;
  const partnerId = s.requesterId === me ? s.targetUserId : s.requesterId;
  const partner = await api(`/api/users/${partnerId}`).then((p) => p.fullName).catch(() => "Partner");
  const open = s.status === "SCHEDULED" || s.status === "ONGOING";

  view.innerHTML = `
    <a href="#/sessions" class="small">&larr; Daftar sesi</a>
    <div class="gk-card p-4 mt-2">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h1 class="h5 fw-bold m-0">Sesi dengan ${esc(partner)}</h1>${statusBadge(s.status)}
      </div>
      <div id="msg"></div>
      <div class="row g-3 mb-3">
        <div class="col-md-6"><div class="small text-muted">Waktu</div><div class="fw-bold">${fmtDate(s.scheduledAt)}</div></div>
        <div class="col-md-3"><div class="small text-muted">Durasi</div><div class="fw-bold">${esc(s.durationMinutes)} menit</div></div>
        <div class="col-md-3"><div class="small text-muted">Platform</div><div class="fw-bold">${esc(s.meetingProvider)}</div></div>
      </div>
      ${open && s.meetingUrl ? `<a class="gk-btn gk-btn--primary mb-3" href="${esc(s.meetingUrl)}" target="_blank" rel="noopener noreferrer"><i class="bi bi-camera-video-fill me-1"></i>Buka ruang meeting</a>` : ""}
      ${open ? `
      <form id="resched" class="border rounded-3 p-3 mb-3">
        <div class="fw-bold small mb-2"><i class="bi bi-calendar-check me-1"></i>Ubah jadwal</div>
        <div class="row g-2 align-items-end">
          <div class="col-md-6"><label class="form-label small">Tanggal &amp; jam baru</label>
            <input type="datetime-local" name="at" class="form-control" required value="${toLocalInput(s.scheduledAt)}"></div>
          <div class="col-md-3"><label class="form-label small">Durasi (menit)</label>
            <input type="number" name="dur" class="form-control" min="15" max="240" value="${esc(s.durationMinutes)}"></div>
          <div class="col-md-3"><button class="gk-btn gk-btn--secondary w-100">Simpan jadwal</button></div>
        </div>
      </form>
      <div class="d-flex gap-2">
        <button class="gk-btn text-white" style="background:var(--gk-success)" data-status="COMPLETED">Tandai selesai</button>
        <button class="gk-btn gk-btn--danger" data-status="CANCELLED">Batalkan sesi</button>
      </div>` : ""}
    </div>
    ${s.status === "COMPLETED" ? `
    <form id="review" class="gk-card p-4 mt-3">
      <h2 class="h6 fw-bold">Beri ulasan</h2>
      <div class="mb-2"><select name="rating" class="form-select" style="max-width:160px" required>
        ${[5, 4, 3, 2, 1].map((n) => `<option value="${n}">${n} ★</option>`).join("")}</select></div>
      <textarea name="comment" class="form-control mb-2" rows="3" maxlength="2000" placeholder="Ceritakan pengalaman belajarmu (opsional)"></textarea>
      <button class="gk-btn text-white" style="background:var(--gk-warning)">Kirim ulasan</button>
    </form>` : ""}`;

  const msg = (m, k) => (view.querySelector("#msg").innerHTML = alertBox(m, k));

  view.querySelector("#resched")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const d = new FormData(ev.target);
    try {
      await api(`/api/sessions/${id}`, {
        method: "PATCH",
        query: { at: new Date(d.get("at")).toISOString(), durationMinutes: d.get("dur") },
      });
      toast("Jadwal diperbarui");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  view.querySelectorAll("button[data-status]").forEach((b) => (b.onclick = async () => {
    const next = b.dataset.status;
    if (!confirm(next === "CANCELLED" ? "Batalkan sesi ini?" : "Tandai sesi ini selesai?")) return;
    try {
      await api(`/api/sessions/${id}/status`, { method: "POST", body: { status: next } });
      toast("Status diperbarui");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  }));

  view.querySelector("#review")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const d = new FormData(ev.target);
    try {
      await api("/api/reviews", { method: "POST",
        body: { learningSessionId: id, rating: Number(d.get("rating")), comment: d.get("comment") || null } });
      toast("Terima kasih atas ulasanmu");
      ev.target.remove();
    } catch (e) { msg(e.message); }
  });
}
