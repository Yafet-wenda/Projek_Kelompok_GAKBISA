import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, fmtDate, toast, alertBox } from "../ui.js";

// Data beranda terakhir per pengguna, supaya kunjungan berikutnya langsung tampil
// sementara data terbaru diambil di belakang layar.
let last = null; // { uid, data }

function draw(view, d, tasks = []) {
  const stat = (icon, label, value, href) => `
    <a href="#${href}" class="col-md-4 text-decoration-none">
      <div class="gk-card p-3 h-100"><i class="bi ${icon} fs-4 text-primary"></i>
        <div class="fs-3 fw-bold" style="color:var(--gk-ink)">${esc(value)}</div><div class="small text-muted">${label}</div></div>
    </a>`;

  const matches = d.topMatches.length
    ? d.topMatches.map((m) => `
        <div class="d-flex justify-content-between align-items-start py-2 border-bottom">
          <div><div><a href="#/profile/${esc(m.userId)}" class="fw-bold text-decoration-none" style="color:var(--gk-ink)" title="Lihat profil publik">${esc(m.fullName)}</a></div>
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
  view.insertAdjacentHTML("beforeend", `<form id="app-feedback" class="gk-card p-3 mt-3"><h2 class="h6 fw-bold">Masukan untuk GAKBISA</h2><div id="feedback-msg"></div><div class="d-flex gap-2"><textarea name="message" class="form-control" maxlength="2000" minlength="5" rows="2" placeholder="Ceritakan kendala atau saranmu…" required></textarea><button class="gk-btn text-white align-self-end" style="background:var(--gk-primary)">Kirim</button></div></form>`);
  view.querySelector("#app-feedback").onsubmit = async (ev) => { ev.preventDefault(); try { await api("/api/feedback", { method: "POST", body: { message: new FormData(ev.currentTarget).get("message") } }); ev.currentTarget.reset(); toast("Terima kasih atas masukannya"); } catch (e) { view.querySelector("#feedback-msg").innerHTML = alertBox(e.message); } };
  if (tasks.length) view.insertAdjacentHTML("beforeend", `<section class="gk-card p-3 mt-3"><h2 class="h6 fw-bold"><i class="bi bi-list-check me-2"></i>Perlu ditindaklanjuti</h2><ul class="mb-0">${tasks.map((t) => `<li class="py-1"><a href="#${t.href}">${esc(t.label)}</a></li>`).join("")}</ul></section>`);
}

export default async function page(view) {
  const uid = currentUser().id;
  if (last && last.uid === uid) draw(view, last.data, last.tasks);
  const [data, inbox, outbox, sessions, reviews] = await Promise.all([
    api("/api/dashboard"),
    api("/api/barter/inbox").catch(() => []),
    api("/api/barter/outbox").catch(() => []),
    api("/api/sessions").catch(() => []),
    api("/api/reviews/mine").catch(() => []),
  ]);
  const allBarters = [...inbox, ...outbox];
  const tasks = [];
  if (allBarters.some((b) => b.status === "PENDING")) tasks.push({ label: "Tinjau permintaan barter yang menunggu respons", href: "/barter" });
  const accepted = allBarters.filter((b) => b.status === "ACCEPTED");
  if (accepted.some((b) => !sessions.some((s) => s.barterRequestId === b.id && ["SCHEDULED", "ONGOING", "PENDING_RESCHEDULE"].includes(s.status)))) tasks.push({ label: "Jadwalkan sesi untuk barter yang diterima", href: "/barter" });
  const reviewed = new Set(reviews.map((r) => r.sessionId));
  if (sessions.some((s) => s.status === "COMPLETED" && !reviewed.has(s.id))) tasks.push({ label: "Berikan ulasan untuk sesi yang selesai", href: "/sessions" });
  await Promise.all(accepted.map(async (b) => {
    const goals = await api(`/api/barter/${b.id}/goals`).catch(() => []);
    if (goals.some((g) => !g.isDone)) tasks.push({ label: "Perbarui target belajar yang belum selesai", href: "/barter" });
  }));
  last = { uid, data, tasks };
  draw(view, data, tasks);
}
