import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, fmtDate } from "../ui.js";

// Data beranda terakhir per pengguna, supaya kunjungan berikutnya langsung tampil
// sementara data terbaru diambil di belakang layar.
let last = null; // { uid, data }

function draw(view, d) {
  const stat = (icon, label, value, href) => `
    <a href="#${href}" class="col-md-4 text-decoration-none">
      <div class="gk-card p-3 h-100"><i class="bi ${icon} fs-4 text-primary"></i>
        <div class="fs-3 fw-bold" style="color:var(--gk-ink)">${esc(value)}</div><div class="small text-muted">${label}</div></div>
    </a>`;

  const matches = d.topMatches.length
    ? d.topMatches.map((m) => `
        <div class="d-flex justify-content-between align-items-start py-2 border-bottom">
          <div><div class="fw-bold">${esc(m.fullName)}</div>
            <div class="small text-muted">${esc(m.campus || "Mahasiswa GAKBISA")}</div>
            <div class="small">${m.reasons.map(esc).join(" · ")}</div></div>
          <span class="gk-badge">${esc(m.score)} poin</span>
        </div>`).join("")
    : '<p class="text-muted small m-0">Belum ada rekomendasi. Lengkapi daftar keahlian yang kamu tawarkan dan ingin dipelajari.</p>';

  const notifs = d.recentNotifications.length
    ? d.recentNotifications.map((n) => `
        <div class="py-2 border-bottom ${n.read ? "text-muted" : "fw-bold"}">
          <div>${esc(n.title)}</div><div class="small fw-normal">${esc(n.body)}</div>
          <div class="small text-muted fw-normal">${fmtDate(n.createdAt)}</div></div>`).join("")
    : '<p class="text-muted small m-0">Tidak ada notifikasi.</p>';

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3">Halo, ${esc(currentUser().fullName)}</h1>
    <div class="row g-3 mb-4">
      ${stat("bi-arrow-left-right", "Permintaan barter menunggu", d.pendingBarters, "/barter")}
      ${stat("bi-calendar-event", "Sesi aktif", d.activeSessions, "/sessions")}
      ${stat("bi-bell", "Notifikasi belum dibaca", d.unreadNotifications, "/notifications")}
    </div>
    <div class="row g-3">
      <div class="col-md-6"><div class="gk-card p-3"><h2 class="h6 fw-bold">Rekomendasi partner</h2>${matches}</div></div>
      <div class="col-md-6"><div class="gk-card p-3"><h2 class="h6 fw-bold">Notifikasi terbaru</h2>${notifs}</div></div>
    </div>`;
}

export default async function page(view) {
  const uid = currentUser().id;
  if (last && last.uid === uid) draw(view, last.data);
  const data = await api("/api/dashboard");
  last = { uid, data };
  draw(view, data);
}