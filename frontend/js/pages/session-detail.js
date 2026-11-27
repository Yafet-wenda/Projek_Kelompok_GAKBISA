import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { launchInlineMeeting } from "../meeting.js";
import { esc, fmtDate, statusBadge, toast, alertBox, toLocalInput } from "../ui.js";

let activeChatPollTimer = null;
let activeChatHashCleanup = null;

export default async function page(view, { id }) {
  if (activeChatPollTimer) clearInterval(activeChatPollTimer);
  if (activeChatHashCleanup) window.removeEventListener("hashchange", activeChatHashCleanup);
  activeChatPollTimer = null;
  activeChatHashCleanup = null;

  const s = await api(`/api/sessions/${id}`);
  const me = currentUser().id;
  const partnerId = s.requesterId === me ? s.targetUserId : s.requesterId;
  const [partner, myReviews, scheduleHistory] = await Promise.all([
    partnerId ? api(`/api/users/${partnerId}`).then((p) => p.fullName).catch(() => "Partner") : Promise.resolve("Kelompok Belajar"),
    s.status === "COMPLETED" ? api("/api/reviews/mine").catch(() => []) : Promise.resolve([]),
    api(`/api/sessions/${id}/history`).catch(() => []),
  ]);
  const alreadyReviewed = myReviews.some((r) => String(r.sessionId) === String(id));
  let feedback = [];
  if (s.status === "COMPLETED") feedback = await api(`/api/sessions/${id}/feedback`).catch(() => []);
  let canFollowup = false;
  if (s.status === "COMPLETED" && s.barterRequestId) {
    const goals = await api(`/api/barter/${s.barterRequestId}/goals`).catch(() => []);
    canFollowup = goals.some((g) => !g.isDone) || feedback.some((f) => f.needsFollowup);
  }
  const attendanceOpen = s.barterRequestId && s.status !== "CANCELLED" && Date.now() >= new Date(s.scheduledAt).getTime() + s.durationMinutes * 60_000 - 15 * 60_000;
  const attendance = attendanceOpen ? await api(`/api/sessions/${id}/attendance`).catch(() => ({ attended: false })) : null;
  const pendingInitialSchedule = Boolean(s.barterRequestId) && s.status === "SCHEDULED" && !s.scheduleConfirmed;
  const proposedInitialScheduleByMe = pendingInitialSchedule && s.scheduleProposedBy === me;
  const open = s.status === "PENDING_RESCHEDULE" || (s.scheduleConfirmed && (s.status === "SCHEDULED" || s.status === "ONGOING"));
  const isPendingReschedule = s.status === "PENDING_RESCHEDULE";
  const iProposed = isPendingReschedule && s.rescheduleProposedBy === me;
  const partnerProposed = isPendingReschedule && s.rescheduleProposedBy !== me;

  /* ── Helper: render blok reschedule ─────────────────────────────────────── */
  const rescheduleBlock = () => {
    if (!open) return "";

    // Kasus A: Ada proposal aktif dari saya → tampilkan proposal + tombol tarik kembali
    if (iProposed) {
      return `
      <div class="gk-reschedule-banner gk-reschedule-banner--mine border rounded-3 p-3 mb-3">
        <div class="d-flex align-items-center gap-2 mb-2">
          <i class="bi bi-clock-history text-warning fs-5"></i>
          <span class="fw-bold">Menunggu konfirmasi partner</span>
        </div>
        <p class="small text-muted mb-2">Kamu mengusulkan jadwal baru. Menunggu ${esc(partner)} menerima atau menolak.</p>
        <div class="row g-2 mb-3">
          <div class="col-md-6"><div class="small text-muted">Jadwal diusulkan</div>
            <div class="fw-bold">${esc(fmtDate(s.rescheduleProposedTime))}</div></div>
          ${s.rescheduleProposedDuration ? `<div class="col-md-3"><div class="small text-muted">Durasi baru</div>
            <div class="fw-bold">${esc(s.rescheduleProposedDuration)} menit</div></div>` : ""}
        </div>
        <button id="btn-withdraw" class="gk-btn gk-btn--secondary gk-btn--sm">
          <i class="bi bi-x-circle me-1"></i>Tarik kembali usulan
        </button>
      </div>`;
    }

    // Kasus B: Ada proposal aktif dari partner → tampilkan detail + tombol terima/tolak
    if (partnerProposed) {
      return `
      <div class="gk-reschedule-banner gk-reschedule-banner--partner border rounded-3 p-3 mb-3">
        <div class="d-flex align-items-center gap-2 mb-2">
          <i class="bi bi-calendar-event text-primary fs-5"></i>
          <span class="fw-bold">${esc(partner)} mengusulkan jadwal baru</span>
        </div>
        <div class="row g-2 mb-3">
          <div class="col-md-6"><div class="small text-muted">Jadwal diusulkan</div>
            <div class="fw-bold">${esc(fmtDate(s.rescheduleProposedTime))}</div></div>
          ${s.rescheduleProposedDuration ? `<div class="col-md-3"><div class="small text-muted">Durasi baru</div>
            <div class="fw-bold">${esc(s.rescheduleProposedDuration)} menit</div></div>` : ""}
          <div class="col-md-3"><div class="small text-muted">Jadwal saat ini</div>
            <div class="text-muted small">${esc(fmtDate(s.scheduledAt))}</div></div>
        </div>
        <div class="d-flex gap-2">
          <button id="btn-confirm-resched" class="gk-btn text-white" style="background:var(--gk-success)">
            <i class="bi bi-check-circle me-1"></i>Terima jadwal baru
          </button>
          <button id="btn-reject-resched" class="gk-btn gk-btn--danger">
            <i class="bi bi-x-circle me-1"></i>Tolak
          </button>
        </div>
      </div>`;
    }

    // Kasus C: Tidak ada proposal aktif → tampilkan form usul jadwal baru
    if (s.status === "SCHEDULED") {
      return `
      <form id="resched" class="border rounded-3 p-3 mb-3">
        <div class="fw-bold small mb-1"><i class="bi bi-calendar-check me-1"></i>Usulkan jadwal baru</div>
        <div class="small text-muted mb-2">
          <i class="bi bi-info-circle me-1"></i>Partner harus menyetujui sebelum jadwal resmi berubah.
        </div>
        <div class="row g-2 align-items-end">
          <div class="col-md-6"><label class="form-label small">Tanggal &amp; jam baru</label>
            <input type="datetime-local" name="at" class="form-control" required value="${esc(toLocalInput(s.scheduledAt))}"></div>
          <div class="col-md-3"><label class="form-label small">Durasi (menit)</label>
            <input type="number" name="dur" class="form-control" min="15" max="240" value="${esc(s.durationMinutes)}"></div>
          <div class="col-md-3"><button class="gk-btn gk-btn--secondary w-100">
            <i class="bi bi-send me-1"></i>Kirim usulan
          </button></div>
        </div>
      </form>`;
    }

    return "";
  };

  const initialScheduleBlock = pendingInitialSchedule ? (proposedInitialScheduleByMe
    ? `<div class="alert alert-warning mt-3 mb-3"><i class="bi bi-hourglass-split me-1"></i>Usulan jadwal menunggu konfirmasi ${esc(partner)}. Meeting baru tersedia setelah partner menyetujuinya.</div>`
    : `<div class="gk-card p-3 mt-3 mb-3"><h2 class="h6 fw-bold">Konfirmasi jadwal sesi</h2><p class="small text-muted">Partner mengusulkan sesi pada ${esc(fmtDate(s.scheduledAt))} selama ${esc(s.durationMinutes)} menit. Pilih terima atau tolak.</p><div class="d-flex gap-2"><button id="btn-confirm-schedule" class="gk-btn text-white" style="background:var(--gk-success)"><i class="bi bi-check-circle me-1"></i>Terima jadwal</button><button id="btn-reject-schedule" class="gk-btn gk-btn--danger"><i class="bi bi-x-circle me-1"></i>Tolak jadwal</button></div></div>`)
    : "";
  const attendanceBlock = attendanceOpen ? attendance?.attended
    ? `<div class="alert alert-success mt-3 mb-0"><i class="bi bi-check-circle me-1"></i>Kehadiranmu sudah dikonfirmasi.</div>`
    : `<div class="border rounded-3 p-3 mt-3"><div class="fw-bold small mb-1">Konfirmasi kehadiran</div><p class="small text-muted mb-2">Kamu dapat check-in saat sesi mendekati waktu selesai.</p><button id="btn-attendance" class="gk-btn gk-btn--secondary gk-btn--sm"><i class="bi bi-person-check me-1"></i>Konfirmasi Hadir</button></div>`
    : "";
  const myFeedback = feedback.find((item) => String(item.userId) === String(me));
  const feedbackBlock = s.status !== "COMPLETED" ? "" : `
    <section class="gk-card p-4 mt-3">
      <h2 class="h6 fw-bold mb-3">Umpan balik sesi</h2>
      ${feedback.length ? feedback.map((item) => `
        <article class="border rounded-3 p-3 mb-2">
          <div class="d-flex justify-content-between gap-2 flex-wrap"><strong>${String(item.userId) === String(me) ? "Umpan balikmu" : "Umpan balik partner"}</strong><span class="small text-muted">${esc(fmtDate(item.createdAt))}</span></div>
          <div class="small mt-2"><span class="fw-bold">Yang berhasil dicapai:</span><p class="mb-2 text-break">${esc(item.achieved)}</p></div>
          ${item.remaining ? `<div class="small"><span class="fw-bold">Yang masih perlu dipelajari:</span><p class="mb-2 text-break">${esc(item.remaining)}</p></div>` : ""}
          <div class="small ${item.needsFollowup ? "text-primary" : "text-muted"}">${item.needsFollowup ? "Meminta sesi lanjutan" : "Tidak meminta sesi lanjutan"}</div>
        </article>`).join("") : '<p class="small text-muted">Belum ada umpan balik untuk sesi ini.</p>'}
      ${myFeedback ? '<div class="small text-success mt-2"><i class="bi bi-check-circle me-1"></i>Umpan balikmu sudah dikirim.</div>' : `
        <form id="session-feedback-form" class="border-top pt-3 mt-3">
          <h3 class="small fw-bold">Bagikan hasil belajarmu</h3>
          <label class="form-label small">Hal yang berhasil dicapai<textarea name="achieved" class="form-control" maxlength="2000" rows="3" required></textarea></label>
          <label class="form-label small">Hal yang masih perlu dipelajari (opsional)<textarea name="remaining" class="form-control" maxlength="2000" rows="2"></textarea></label>
          <label class="form-check small mb-3"><input class="form-check-input" type="checkbox" name="needsFollowup"><span class="form-check-label">Saya ingin menjadwalkan sesi lanjutan</span></label>
          <button class="gk-btn text-white" style="background:var(--gk-primary)">Kirim umpan balik</button>
        </form>`}
    </section>`;
  const historyBlock = scheduleHistory.length ? `
    <details class="gk-card p-3 mt-3">
      <summary class="fw-bold">Riwayat perubahan jadwal (${esc(scheduleHistory.length)})</summary>
      <ol class="small mb-0 mt-3 ps-3">${scheduleHistory.map((item) => `
        <li class="mb-3">
          <div><strong>${esc(item.actorName)}</strong> · ${esc(item.decision)} · ${esc(fmtDate(item.createdAt))}</div>
          <div class="text-muted">${esc(fmtDate(item.oldScheduledAt))} (${esc(item.oldDurationMinutes)} menit) → ${esc(fmtDate(item.proposedScheduledAt))} (${esc(item.proposedDurationMinutes)} menit)</div>
        </li>`).join("")}</ol>
    </details>` : "";

  view.innerHTML = `
    <a href="#/sessions" class="small">&larr; Daftar sesi</a>
    <div class="gk-card p-4 mt-2">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h1 class="h5 fw-bold m-0">Sesi dengan ${esc(partner)}</h1>${statusBadge(s.status)}
      </div>
      <div id="msg"></div>
      <div class="row g-3 mb-3">
        <div class="col-md-6"><div class="small text-muted">Waktu</div><div class="fw-bold">${esc(fmtDate(s.scheduledAt))}</div></div>
        <div class="col-md-3"><div class="small text-muted">Durasi</div><div class="fw-bold">${esc(s.durationMinutes)} menit</div></div>
        <div class="col-md-3"><div class="small text-muted">Platform</div><div class="fw-bold">${esc(s.meetingProvider)}</div></div>
      </div>
      ${initialScheduleBlock}
      ${open && s.scheduleConfirmed ? `<button type="button" id="btn-join-meeting" class="gk-btn text-white mb-3" style="background:var(--gk-primary)"><i class="bi bi-camera-video-fill me-1"></i>Gabung Meeting</button><div id="meeting-host" class="mb-3"></div>` : ""}
      ${rescheduleBlock()}
      ${attendanceBlock}
      ${(s.status === "ONGOING" || (s.status === "SCHEDULED" && s.scheduleConfirmed)) ? `
      <div class="d-flex gap-2">
        <button class="gk-btn text-white" style="background:var(--gk-success)" data-status="COMPLETED">Tandai selesai</button>
        <button class="gk-btn gk-btn--danger" data-status="CANCELLED">Batalkan sesi</button>
      </div>` : ""}
      <div class="mt-3 pt-3 border-top d-flex justify-content-between align-items-center">
        <a href="#/profile/${esc(partnerId)}" class="small"><i class="bi bi-person me-1"></i>Lihat profil ${esc(partner)}</a>
        <button class="btn btn-sm btn-link text-danger text-decoration-none" id="btn-report-session">
          <i class="bi bi-flag me-1"></i>Laporkan kendala / partner
        </button>
      </div>
    </div>
    ${historyBlock}
    <dialog id="session-report-dlg" class="gk-dialog"></dialog>

    <!-- Chat Section -->
    <section class="gk-card p-4 mt-3" id="chat-section">
      <h2 class="h6 fw-bold mb-3">
        <i class="bi bi-chat-dots me-2"></i>Obrolan Sesi dengan ${esc(partner)}
      </h2>
      <div id="chat-messages" class="gk-chat-msgs"></div>
      <div id="chat-err"></div>
      <form id="chat-form" class="d-flex gap-2 mt-2 align-items-center">
        <input name="msg" class="form-control" placeholder="Ketik pesan…" autocomplete="off" required maxlength="2000">
        <button class="gk-btn gk-btn--chat-send text-white" type="submit">
          <i class="bi bi-send-fill me-1"></i>Kirim
        </button>
      </form>
    </section>

    ${feedbackBlock}
    ${s.status === "COMPLETED" && !alreadyReviewed ? `
    <form id="review" class="gk-card p-4 mt-3">
      <h2 class="h6 fw-bold">Beri ulasan</h2>
      <div class="mb-2"><select name="rating" class="form-select" style="max-width:160px" required>
        ${[5, 4, 3, 2, 1].map((n) => `<option value="${n}">${n} ★</option>`).join("")}</select></div>
      <textarea name="comment" class="form-control mb-2" rows="3" maxlength="2000" placeholder="Ceritakan pengalaman belajarmu (opsional)"></textarea>
      <button class="gk-btn text-white" style="background:var(--gk-warning)">Kirim ulasan</button>
    </form>` : s.status === "COMPLETED" ? '<div class="gk-card p-3 mt-3 text-success"><i class="bi bi-check-circle me-1"></i>Ulasan untuk sesi ini sudah dikirim.</div>' : ""}
    ${canFollowup ? (() => {
      const durations = Array.from(new Set([30, 60, 90, 120, s.durationMinutes].filter(Boolean))).sort((a, b) => a - b);
      return `<form id="followup-form" class="gk-card p-4 mt-3"><h2 class="h6 fw-bold">Lanjutkan belajar</h2><p class="small text-muted">Masih ada target atau partner meminta sesi lanjutan. Usulkan jadwal; sesi aktif setelah partner menyetujuinya.</p><div class="row g-2"><div class="col-md-7"><input name="at" type="datetime-local" class="form-control" min="${esc(toLocalInput(Date.now() + 60000))}" required></div><div class="col-md-3"><select name="duration" class="form-select">${durations.map((d) => `<option value="${esc(d)}" ${d === s.durationMinutes ? "selected" : ""}>${esc(d)} menit</option>`).join("")}</select></div><div class="col-md-2"><button class="gk-btn text-white" style="background:var(--gk-primary)">Usulkan</button></div></div></form>`;
    })() : ""}`;

  const msg = (m, k) => (view.querySelector("#msg").innerHTML = alertBox(m, k));

  view.querySelector("#btn-join-meeting")?.addEventListener("click", () =>
    launchInlineMeeting(id, view.querySelector("#meeting-host"), view.querySelector("#msg"))
  );

  view.querySelector("#btn-confirm-schedule")?.addEventListener("click", async () => {
    try {
      await api(`/api/sessions/${id}/schedule/confirm`, { method: "POST" });
      toast("Jadwal disetujui. Meeting sudah dapat dibuka mendekati waktu sesi.");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  view.querySelector("#btn-reject-schedule")?.addEventListener("click", async () => {
    if (!confirm("Tolak jadwal yang diusulkan partner?")) return;
    try {
      await api(`/api/sessions/${id}/schedule/reject`, { method: "POST" });
      toast("Jadwal ditolak");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  view.querySelector("#btn-attendance")?.addEventListener("click", async (ev) => {
    const button = ev.currentTarget;
    button.disabled = true;
    try {
      await api(`/api/sessions/${id}/attendance`, { method: "POST" });
      toast("Kehadiran berhasil dikonfirmasi");
      await page(view, { id });
    } catch (e) {
      msg(e.message);
      button.disabled = false;
    }
  });

  // ── Form usulan jadwal baru (kirim proposal) ─────────────────────────────
  view.querySelector("#resched")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const d = new FormData(ev.target);
    try {
      await api(`/api/sessions/${id}`, {
        method: "PATCH",
        query: { at: new Date(d.get("at")).toISOString(), durationMinutes: d.get("dur") },
      });
      toast("Usulan jadwal baru dikirim ke partner — menunggu konfirmasi");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  // ── Tombol Terima jadwal baru (pihak penerima) ───────────────────────────
  view.querySelector("#btn-confirm-resched")?.addEventListener("click", async () => {
    if (!confirm("Terima jadwal baru yang diusulkan partner?")) return;
    try {
      await api(`/api/sessions/${id}/reschedule/confirm`, { method: "POST" });
      toast("Jadwal baru diterima. Sesi diperbarui.");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  // ── Tombol Tolak (pihak penerima menolak) ────────────────────────────────
  view.querySelector("#btn-reject-resched")?.addEventListener("click", async () => {
    if (!confirm("Tolak usulan jadwal ini? Jadwal sesi tidak akan berubah.")) return;
    try {
      await api(`/api/sessions/${id}/reschedule/reject`, { method: "POST" });
      toast("Usulan jadwal ditolak. Jadwal tetap seperti semula.");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  // ── Tombol Tarik kembali usulan (pengusul membatalkan) ───────────────────
  view.querySelector("#btn-withdraw")?.addEventListener("click", async () => {
    if (!confirm("Tarik kembali usulan jadwal ini?")) return;
    try {
      await api(`/api/sessions/${id}/reschedule/reject`, { method: "POST" });
      toast("Usulan jadwal ditarik kembali.");
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
      await api("/api/reviews", {
        method: "POST",
        body: { learningSessionId: id, rating: Number(d.get("rating")), comment: d.get("comment") || null }
      });
      toast("Terima kasih atas ulasanmu");
      ev.target.remove();
    } catch (e) { msg(e.message); }
  });

  view.querySelector("#session-feedback-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const data = new FormData(ev.currentTarget);
    try {
      await api(`/api/sessions/${id}/feedback`, { method: "POST", body: {
        achieved: data.get("achieved").toString().trim(),
        remaining: data.get("remaining").toString().trim(),
        needsFollowup: Boolean(data.get("needsFollowup")),
      } });
      toast("Umpan balik berhasil dikirim");
      await page(view, { id });
    } catch (e) { msg(e.message); }
  });

  view.querySelector("#followup-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const data = new FormData(ev.currentTarget);
    try {
      const created = await api(`/api/sessions/${id}/follow-up`, { method: "POST", body: {
        scheduledAt: new Date(data.get("at")).toISOString(),
        durationMinutes: Number(data.get("duration")),
      } });
      toast("Usulan sesi lanjutan dikirim; menunggu konfirmasi partner");
      window.location.hash = `#/sessions/${created.id}`;
    } catch (e) { msg(e.message); }
  });

  const repBtn = view.querySelector("#btn-report-session");
  const repDlg = view.querySelector("#session-report-dlg");
  if (repBtn && repDlg) {
    repBtn.onclick = () => {
      repDlg.innerHTML = `
        <form method="dialog" id="form-session-rep" class="gk-card p-4 d-flex flex-column gap-3">
          <div class="d-flex justify-content-between align-items-start">
            <h2 class="h5 fw-bold m-0 text-danger"><i class="bi bi-shield-exclamation me-1"></i>Laporkan Sesi / Partner</h2>
            <button type="button" class="gk-icon-btn" data-close aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
          </div>
          <div id="session-rep-msg"></div>
          <div>
            <label class="form-label small fw-bold">Alasan Pelaporan</label>
            <textarea name="reason" class="form-control" rows="3" required maxlength="1000"
                      placeholder="Jelaskan kendala (partner tidak hadir, perilaku tidak pantas, dsb)…"></textarea>
          </div>
          <div class="d-flex justify-content-end gap-2">
            <button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button>
            <button class="gk-btn gk-btn--danger">Kirim Laporan</button>
          </div>
        </form>`;
      repDlg.querySelectorAll("[data-close]").forEach((b) => (b.onclick = () => repDlg.close()));
      repDlg.querySelector("#form-session-rep").onsubmit = async (ev) => {
        ev.preventDefault();
        const reason = new FormData(ev.target).get("reason").toString().trim();
        try {
          await api("/api/reports", {
            method: "POST",
            body: { targetType: "USER", targetId: partnerId, reason },
          });
          repDlg.close();
          toast("Laporan telah dikirim ke moderator");
        } catch (e) {
          repDlg.querySelector("#session-rep-msg").innerHTML = alertBox(e.message);
        }
      };
      repDlg.showModal();
    };
  }

  // ── Chat ────────────────────────────────────────────────────────────────────
  const chatBox = view.querySelector("#chat-messages");
  const chatErr = view.querySelector("#chat-err");
  const chatForm = view.querySelector("#chat-form");

  const bubble = (m) => `
    <div class="gk-chat-row ${m.isMe ? "gk-chat-row--me" : "gk-chat-row--partner"}">
      <div class="gk-chat-bubble ${m.isMe ? "gk-chat-bubble--me" : "gk-chat-bubble--partner"}">
        ${!m.isMe ? `<div class="gk-chat-sender">${esc(m.senderName)}</div>` : ""}
        <div class="gk-chat-text">${esc(m.content)}</div>
        <div class="gk-chat-time">${esc(fmtDate(m.createdAt))}</div>
        <button class="btn btn-link btn-sm p-0 mt-1" data-chat-action="${m.isMe ? "delete" : "report"}" data-message="${esc(m.id)}">${m.isMe ? "Hapus" : "Laporkan"}</button>
      </div>
    </div>`;

  let lastCount = -1;

  async function loadChat(scroll = false) {
    try {
      const msgs = await api(`/api/sessions/${id}/chat`);
      if (msgs.length !== lastCount) {
        lastCount = msgs.length;
        if (msgs.length === 0) {
          chatBox.innerHTML = `<p class="gk-chat-empty">Belum ada pesan. Mulai obrolan dengan ${esc(partner)}!</p>`;
        } else {
          chatBox.innerHTML = msgs.map(bubble).join("");
        }
        scroll = true;
      }
      if (scroll) chatBox.scrollTop = chatBox.scrollHeight;
      chatErr.innerHTML = "";
    } catch (e) {
      chatErr.innerHTML = alertBox("Gagal memuat pesan: " + e.message, "warning");
    }
  }

  chatBox.onclick = async (ev) => {
    const button = ev.target.closest("button[data-chat-action]");
    if (!button) return;
    const messageId = button.dataset.message;
    try {
      if (button.dataset.chatAction === "delete") {
        if (!confirm("Hapus pesan ini?")) return;
        await api(`/api/sessions/${id}/chat/${messageId}`, { method: "DELETE" });
        toast("Pesan dihapus");
      } else {
        const input = prompt("Jelaskan alasan laporan (5–500 karakter)");
        if (input === null) return;
        const reason = input.trim();
        if (reason.length < 5 || reason.length > 500) {
          chatErr.innerHTML = alertBox("Alasan laporan harus 5–500 karakter");
          return;
        }
        await api(`/api/sessions/${id}/chat/${messageId}/report`, { method: "POST", body: { reason } });
        toast("Pesan dilaporkan ke admin");
      }
      lastCount = -1;
      await loadChat(false);
    } catch (e) {
      chatErr.innerHTML = alertBox(e.message);
    }
  };

  chatForm?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const input = chatForm.querySelector("input[name=msg]");
    const text = input.value.trim();
    if (!text) return;
    const btn = chatForm.querySelector("button[type=submit]");
    btn.disabled = true;
    try {
      await api(`/api/sessions/${id}/chat`, { method: "POST", body: { message: text } });
      input.value = "";
      await loadChat(true);
    } catch (e) {
      chatErr.innerHTML = alertBox("Gagal mengirim pesan: " + e.message);
    } finally {
      btn.disabled = false;
      input.focus();
    }
  });

  // Load pertama & polling tiap 10 detik
  await loadChat(true);
  const timer = setInterval(() => loadChat(false), 10_000);
  activeChatPollTimer = timer;

  // Bersihkan timer saat navigasi hash berubah
  const stopPoll = () => {
    clearInterval(timer);
    if (activeChatPollTimer === timer) activeChatPollTimer = null;
    window.removeEventListener("hashchange", stopPoll);
    if (activeChatHashCleanup === stopPoll) activeChatHashCleanup = null;
  };
  activeChatHashCleanup = stopPoll;
  window.addEventListener("hashchange", stopPoll);
}
