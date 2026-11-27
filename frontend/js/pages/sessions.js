import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, fmtDate, statusBadge } from "../ui.js";

const names = new Map();
async function nameOf(id) {
  if (!names.has(id)) {
    names.set(id, api(`/api/users/${id}`).then((p) => p.fullName).catch(() => "Partner"));
  }
  return names.get(id);
}

export default async function page(view) {
  const [list, inbox, outbox] = await Promise.all([
    api("/api/sessions"),
    api("/api/barter/inbox"),
    api("/api/barter/outbox"),
  ]);
  const me = currentUser().id;
  list.sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));
  const partners = await Promise.all(list.map((s) => nameOf(s.requesterId === me ? s.targetUserId : s.requesterId)));

  const rows = list.map((s, i) => `
    <a href="#/sessions/${s.id}" class="gk-card p-3 mb-2 d-flex justify-content-between align-items-center text-decoration-none text-dark">
      <div><div class="fw-bold">Sesi dengan ${esc(partners[i])}</div>
        <div class="small text-muted"><i class="bi bi-clock me-1"></i>${fmtDate(s.scheduledAt)} · ${esc(s.durationMinutes)} menit</div></div>
      ${statusBadge(s.status)}
    </a>`).join("");

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3">Sesi belajar</h1>
    ${[...inbox, ...outbox].some((b) => b.status === "ACCEPTED") ? `
      <div class="gk-card p-3 mb-3"><h2 class="h6 fw-bold">Barter siap dijadwalkan</h2>
        <p class="small text-muted">Buka menu Barter Keahlian untuk memilih waktu sesi bersama partner.</p>
        <a href="#/barter" class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)">Buka barter diterima</a></div>` : ""}
    ${rows || '<p class="text-muted">Belum ada sesi. Sesi dijadwalkan dari barter yang sudah diterima.</p>'}`;
}
