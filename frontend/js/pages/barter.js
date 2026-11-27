import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, fmtDate, statusBadge, toast, alertBox, toLocalInput } from "../ui.js";

export default async function page(view) {
  const [inbox, outbox] = await Promise.all([api("/api/barter/inbox"), api("/api/barter/outbox")]);
  const me = currentUser().id;
  const barters = [...inbox, ...outbox];
  const agreements = new Map(await Promise.all(barters.filter((b) => b.status === "ACCEPTED").map(async (b) => {
    try { return [b.id, await api(`/api/barter/${b.id}/agreement`)]; }
    catch { return [b.id, null]; }
  })));

  const card = (b) => {
    const incoming = b.targetUserId === me;
    const partner = incoming ? b.requesterName : b.targetUserName;
    const agreement = agreements.get(b.id);
    const actions = b.status === "PENDING"
      ? (incoming
        ? `<button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-success)" data-act="accept" data-id="${esc(b.id)}">Terima</button>
             <button class="gk-btn gk-btn--danger gk-btn--sm" data-act="reject" data-id="${esc(b.id)}">Tolak</button>`
        : `<button class="gk-btn gk-btn--secondary gk-btn--sm" data-act="cancel" data-id="${esc(b.id)}">Batalkan</button>`)
      : "";
    const agreementActions = b.status !== "ACCEPTED" ? "" : agreement ? (() => {
      const myAcceptance = b.requesterId === me ? agreement.requesterAcceptedAt : agreement.targetAcceptedAt;
      const state = agreement.acceptedByBoth ? "Disetujui kedua pihak" : myAcceptance ? "Menunggu persetujuan partner" : "Menunggu persetujuanmu";
      return `<div class="small text-muted mt-2">Kesepakatan: ${esc(agreement.durationMinutes)} menit × ${esc(agreement.sessionCount)} sesi · ${state}</div>
        <div class="mt-2 d-flex gap-2 justify-content-end flex-wrap">
          <button class="gk-btn gk-btn--secondary gk-btn--sm" data-agreement-edit="${esc(b.id)}">Usulkan kesepakatan</button>
          ${!agreement.acceptedByBoth && !myAcceptance ? `<button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-success)" data-agreement-accept="${esc(b.id)}">Setujui kesepakatan</button>` : ""}
          ${agreement.acceptedByBoth ? `<button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)" data-schedule="${esc(b.id)}">Jadwalkan sesi</button>` : ""}
        </div>`;
    })() : `<div class="mt-2 d-flex justify-content-end"><button class="gk-btn gk-btn--secondary gk-btn--sm" data-agreement-edit="${esc(b.id)}">Buat kesepakatan</button></div>`;
    const acceptedActions = b.status === "ACCEPTED" ? `
      <div class="d-flex justify-content-end gap-2 flex-wrap mt-2">
        <button class="gk-btn gk-btn--secondary gk-btn--sm" data-chat="${esc(b.id)}"><i class="bi bi-chat-dots me-1"></i>Chat</button>
        <button class="gk-btn gk-btn--secondary gk-btn--sm" data-goals="${esc(b.id)}"><i class="bi bi-bullseye me-1"></i>Target belajar</button>
        <button class="gk-btn gk-btn--secondary gk-btn--sm" data-close-barter="${esc(b.id)}"><i class="bi bi-check2-circle me-1"></i>Tutup barter</button>
        <button class="gk-btn gk-btn--danger gk-btn--sm" data-dispute="${esc(b.id)}"><i class="bi bi-flag me-1"></i>Ajukan sengketa</button>
      </div>` : b.status === "COMPLETED" ? `
      <div class="d-flex justify-content-end mt-2"><button class="gk-btn gk-btn--secondary gk-btn--sm" data-chat="${esc(b.id)}"><i class="bi bi-chat-dots me-1"></i>Chat</button></div>` : "";
    return `
      <div class="gk-card p-3 mb-2">
        <div class="d-flex justify-content-between flex-wrap gap-2">
          <div><div class="fw-bold">${esc(partner)}</div>
            <div class="small">${incoming ? "Menawarkan" : "Kamu tawarkan"}: <b>${esc(b.offeredSkill)}</b> ⇄ ${incoming ? "meminta" : "kamu minta"}: <b>${esc(b.wantedSkill)}</b></div>
            ${b.message ? `<div class="small text-muted">“${esc(b.message)}”</div>` : ""}
            <div class="small text-muted">${esc(fmtDate(b.createdAt))}</div></div>
          <div class="text-end">${statusBadge(b.status)}<div class="mt-2 d-flex gap-1 justify-content-end">${actions}</div></div>
        </div>
        ${agreementActions}
        ${acceptedActions}
      </div>`;
  };
  const list = (items) => items.length ? items.map(card).join("") : '<p class="text-muted">Belum ada.</p>';

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3">Barter keahlian</h1>
    <div id="msg"></div>
    <h2 class="h6 fw-bold">Masuk</h2>${list(inbox)}
    <h2 class="h6 fw-bold mt-4">Terkirim</h2>${list(outbox)}`;

  view.onclick = async (ev) => {
    const chatButton = ev.target.closest("button[data-chat]");
    if (chatButton) {
      openBarterChat(view, chatButton.dataset.chat);
      return;
    }
    const goalsButton = ev.target.closest("button[data-goals]");
    if (goalsButton) {
      openGoalsDialog(view, goalsButton.dataset.goals);
      return;
    }
    const closeBarterButton = ev.target.closest("button[data-close-barter]");
    if (closeBarterButton) {
      if (!confirm("Ajukan penutupan barter? Barter selesai setelah partner juga menyetujuinya.")) return;
      try {
        const result = await api(`/api/barter/${closeBarterButton.dataset.closeBarter}/close`, { method: "POST" });
        toast(result.status === "COMPLETED" ? "Barter selesai setelah dikonfirmasi kedua pihak" : "Permintaan penutupan dikirim; menunggu konfirmasi partner");
        await page(view);
      } catch (e) { view.querySelector("#msg").innerHTML = alertBox(e.message); }
      return;
    }
    const disputeButton = ev.target.closest("button[data-dispute]");
    if (disputeButton) {
      openDisputeDialog(view, disputeButton.dataset.dispute);
      return;
    }
    const agreementButton = ev.target.closest("button[data-agreement-edit]");
    if (agreementButton) {
      const barter = barters.find((b) => String(b.id) === agreementButton.dataset.agreementEdit);
      if (barter) openAgreementDialog(view, barter, agreements.get(barter.id));
      return;
    }
    const acceptAgreementButton = ev.target.closest("button[data-agreement-accept]");
    if (acceptAgreementButton) {
      try {
        await api(`/api/barter/${acceptAgreementButton.dataset.agreementAccept}/agreement/accept`, { method: "POST" });
        toast("Kesepakatan disetujui");
        await page(view);
      } catch (e) {
        view.querySelector("#msg").innerHTML = alertBox(e.message);
      }
      return;
    }
    const scheduleButton = ev.target.closest("button[data-schedule]");
    if (scheduleButton) {
      const barter = barters.find((b) => String(b.id) === scheduleButton.dataset.schedule);
      if (barter) openScheduleDialog(view, barter, agreements.get(barter.id));
      return;
    }
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

function openAgreementDialog(view, barter, agreement) {
  let dlg = view.querySelector("#agreement-dialog");
  if (!dlg) {
    dlg = document.createElement("dialog");
    dlg.id = "agreement-dialog";
    dlg.className = "gk-dialog";
    view.append(dlg);
  }
  dlg.innerHTML = `
    <form class="gk-card p-4 d-flex flex-column gap-3" id="agreement-form">
      <div class="d-flex justify-content-between align-items-start"><div><h2 class="h5 fw-bold mb-1">Kesepakatan barter</h2><p class="small text-muted mb-0">${esc(barter.wantedSkill)} ⇄ ${esc(barter.offeredSkill)}</p></div><button type="button" class="gk-icon-btn" data-close aria-label="Tutup">×</button></div>
      <div id="agreement-msg"></div>
      <label class="form-label">Durasi setiap sesi (menit)<input class="form-control mt-1" name="duration" type="number" min="15" max="240" required value="${esc(agreement?.durationMinutes || 60)}"></label>
      <label class="form-label">Jumlah sesi yang disepakati<input class="form-control mt-1" name="count" type="number" min="1" max="50" required value="${esc(agreement?.sessionCount || 1)}"></label>
      <p class="small text-muted mb-0">Kesepakatan berlaku setelah kedua pihak menyetujuinya.</p>
      <div class="d-flex justify-content-end gap-2"><button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button><button class="gk-btn text-white" style="background:var(--gk-primary)">Kirim usulan</button></div>
    </form>`;
  dlg.querySelectorAll("[data-close]").forEach((button) => button.addEventListener("click", () => dlg.close()));
  dlg.querySelector("form").onsubmit = async (ev) => {
    ev.preventDefault();
    const data = new FormData(ev.currentTarget);
    try {
      await api(`/api/barter/${barter.id}/agreement`, { method: "POST", body: {
        durationMinutes: Number(data.get("duration")), sessionCount: Number(data.get("count")),
      } });
      dlg.close();
      toast("Usulan kesepakatan dikirim ke partner");
      await page(view);
    } catch (e) {
      dlg.querySelector("#agreement-msg").innerHTML = alertBox(e.message);
    }
  };
  dlg.showModal();
}

async function openBarterChat(view, id) {
  const dlg = document.createElement("dialog");
  dlg.className = "gk-dialog";
  dlg.innerHTML = `<section class="gk-card p-3" style="width:min(700px,90vw)">
    <div class="d-flex justify-content-between"><h2 class="h5 fw-bold">Chat barter</h2><button class="gk-icon-btn" data-close>Tutup</button></div>
    <div id="bc-err"></div>
    <div id="bc-messages" class="gk-chat-msgs" style="height:55vh"></div>
    <form class="d-flex gap-2 mt-2"><input class="form-control" name="message" maxlength="2000" required placeholder="Tulis pesan…"><button class="gk-btn text-white" style="background:var(--gk-primary)">Kirim</button></form>
  </section>`;
  view.append(dlg);
  dlg.showModal();
  dlg.querySelector("[data-close]").onclick = () => dlg.close();
  const box = dlg.querySelector("#bc-messages");
  const err = dlg.querySelector("#bc-err");
  let timer;
  const load = async () => {
    const msgs = await api(`/api/barter/${id}/chat`);
    box.innerHTML = msgs.map((m) => `
      <div class="gk-chat-row ${m.isMe ? "gk-chat-row--me" : "gk-chat-row--partner"}">
        <div class="gk-chat-bubble ${m.isMe ? "gk-chat-bubble--me" : "gk-chat-bubble--partner"}">
          ${!m.isMe ? `<div class="gk-chat-sender">${esc(m.senderName)}</div>` : ""}
          <div class="gk-chat-text">${esc(m.content)}</div>
          <div class="gk-chat-time">${esc(fmtDate(m.createdAt))}</div>
          <div class="d-flex gap-2 mt-1"><button class="btn btn-link btn-sm p-0" data-chat-action="${m.isMe ? "delete" : "report"}" data-message="${esc(m.id)}">${m.isMe ? "Hapus" : "Laporkan"}</button></div>
        </div>
      </div>`).join("") || '<p class="gk-chat-empty">Belum ada pesan.</p>';
    box.scrollTop = box.scrollHeight;
  };
  box.onclick = async (ev) => {
    const btn = ev.target.closest("[data-chat-action]");
    if (!btn) return;
    try {
      if (btn.dataset.chatAction === "delete") {
        await api(`/api/barter/${id}/chat/${btn.dataset.message}`, { method: "DELETE" });
        toast("Pesan dihapus");
      } else {
        const reasonInput = prompt("Jelaskan alasan laporan (5–500 karakter)");
        if (reasonInput === null) return;
        const reason = reasonInput.trim();
        if (reason.length < 5 || reason.length > 500) {
          err.innerHTML = alertBox("Alasan laporan harus 5–500 karakter");
          return;
        }
        await api(`/api/barter/${id}/chat/${btn.dataset.message}/report`, { method: "POST", body: { reason } });
        toast("Pesan dilaporkan ke admin");
      }
      err.innerHTML = "";
      await load();
    } catch (e) { err.innerHTML = alertBox(e.message); }
  };
  await load().catch((e) => { err.innerHTML = alertBox(e.message); });
  timer = setInterval(() => load().catch(() => {}), 8000);
  dlg.addEventListener("close", () => { clearInterval(timer); dlg.remove(); }, { once: true });
  dlg.querySelector("form").onsubmit = async (ev) => {
    ev.preventDefault();
    const input = ev.currentTarget.elements.message;
    try {
      await api(`/api/barter/${id}/chat`, { method: "POST", body: { message: input.value } });
      input.value = "";
      err.innerHTML = "";
      await load();
    } catch (e) { err.innerHTML = alertBox(e.message); }
  };
}

function openGoalsDialog(view, barterId) {
  const dlg = document.createElement("dialog");
  dlg.className = "gk-dialog";
  dlg.innerHTML = `
    <section class="gk-card p-4" style="width:min(650px,90vw)">
      <div class="d-flex justify-content-between align-items-center gap-2"><div><h2 class="h5 fw-bold mb-1">Target belajar</h2><p class="small text-muted mb-0">Catat capaian yang ingin diselesaikan bersama.</p></div><button type="button" class="gk-icon-btn" data-close aria-label="Tutup">Tutup</button></div>
      <div id="goal-msg" class="mt-3"></div>
      <div id="goal-list" class="mt-3"><p class="small text-muted">Memuat target…</p></div>
      <form id="goal-form" class="border-top pt-3 mt-3"><label class="form-label small fw-bold">Target baru<textarea name="description" class="form-control" rows="2" maxlength="500" required placeholder="Contoh: menyelesaikan satu proyek React"></textarea></label><button type="submit" class="gk-btn text-white" style="background:var(--gk-primary)">Tambah target</button></form>
    </section>`;
  view.append(dlg);
  dlg.showModal();
  dlg.addEventListener("close", () => dlg.remove(), { once: true });
  dlg.querySelector("[data-close]").onclick = () => dlg.close();

  const loadGoals = async () => {
    try {
      const goals = await api(`/api/barter/${barterId}/goals`);
      dlg.querySelector("#goal-msg").innerHTML = "";
      dlg.querySelector("#goal-list").innerHTML = goals.length ? goals.map((goal) => `
        <label class="d-flex align-items-start gap-2 border rounded-3 p-2 mb-2">
          <input class="form-check-input mt-1" type="checkbox" data-goal-id="${esc(goal.id)}" ${goal.isDone ? "checked" : ""}>
          <span class="small ${goal.isDone ? "text-decoration-line-through text-muted" : ""}">${esc(goal.description)}</span>
        </label>`).join("") : '<p class="small text-muted mb-0">Belum ada target. Tambahkan target pertama di bawah.</p>';
    } catch (e) {
      dlg.querySelector("#goal-msg").innerHTML = alertBox(e.message);
      dlg.querySelector("#goal-list").innerHTML = "";
    }
  };

  dlg.onchange = async (ev) => {
    const checkbox = ev.target.closest("input[data-goal-id]");
    if (!checkbox) return;
    checkbox.disabled = true;
    try {
      await api(`/api/barter/${barterId}/goals/${checkbox.dataset.goalId}`, { method: "PATCH", body: { done: checkbox.checked } });
      toast(checkbox.checked ? "Target ditandai selesai" : "Target dibuka kembali");
      dlg.close();
      await page(view);
    } catch (e) {
      checkbox.checked = !checkbox.checked;
      checkbox.disabled = false;
      dlg.querySelector("#goal-msg").innerHTML = alertBox(e.message);
    }
  };

  dlg.querySelector("#goal-form").onsubmit = async (ev) => {
    ev.preventDefault();
    const input = new FormData(ev.currentTarget).get("description").toString().trim();
    if (!input) {
      dlg.querySelector("#goal-msg").innerHTML = alertBox("Tuliskan target belajar terlebih dahulu.");
      return;
    }
    const button = ev.currentTarget.querySelector("button[type=submit]");
    button.disabled = true;
    try {
      await api(`/api/barter/${barterId}/goals`, { method: "POST", body: { description: input } });
      toast("Target belajar ditambahkan");
      ev.currentTarget.reset();
      await loadGoals();
    } catch (e) {
      dlg.querySelector("#goal-msg").innerHTML = alertBox(e.message);
    } finally { button.disabled = false; }
  };
  loadGoals();
}

function openDisputeDialog(view, barterId) {
  const dlg = document.createElement("dialog");
  dlg.className = "gk-dialog";
  dlg.innerHTML = `
    <form class="gk-card p-4 d-flex flex-column gap-3" style="width:min(600px,90vw)">
      <div class="d-flex justify-content-between align-items-start"><div><h2 class="h5 fw-bold mb-1">Ajukan sengketa</h2><p class="small text-muted mb-0">Sengketa akan masuk ke antrean admin untuk ditinjau.</p></div><button type="button" class="gk-icon-btn" data-close aria-label="Tutup">Tutup</button></div>
      <div id="dispute-msg"></div>
      <label class="form-label small fw-bold">Jelaskan masalahnya<textarea name="reason" class="form-control" rows="4" maxlength="2000" required></textarea></label>
      <div class="d-flex justify-content-end gap-2"><button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button><button type="submit" class="gk-btn gk-btn--danger">Kirim sengketa</button></div>
    </form>`;
  view.append(dlg);
  dlg.showModal();
  dlg.addEventListener("close", () => dlg.remove(), { once: true });
  dlg.querySelectorAll("[data-close]").forEach((button) => button.onclick = () => dlg.close());
  dlg.querySelector("form").onsubmit = async (ev) => {
    ev.preventDefault();
    const reason = new FormData(ev.currentTarget).get("reason").toString().trim();
    if (!reason) {
      dlg.querySelector("#dispute-msg").innerHTML = alertBox("Alasan sengketa wajib diisi.");
      return;
    }
    const submit = ev.currentTarget.querySelector("button[type=submit]");
    submit.disabled = true;
    try {
      await api("/api/disputes", { method: "POST", body: { barterRequestId: barterId, reason } });
      dlg.close();
      toast("Sengketa dikirim untuk ditinjau admin");
    } catch (e) {
      dlg.querySelector("#dispute-msg").innerHTML = alertBox(e.message);
    } finally { submit.disabled = false; }
  };
}

async function openScheduleDialog(view, barter, agreement) {
  if (!barter) return;
  if (!agreement?.acceptedByBoth) return;
  let dlg = view.querySelector("#schedule-dialog");
  if (!dlg) {
    dlg = document.createElement("dialog");
    dlg.id = "schedule-dialog";
    dlg.className = "gk-dialog";
    view.append(dlg);
  }
  dlg.innerHTML = `
    <form class="gk-card p-4 d-flex flex-column gap-3" id="schedule-form">
      <div class="d-flex justify-content-between align-items-start"><div><h2 class="h5 fw-bold mb-1">Jadwalkan sesi</h2><p class="small text-muted mb-0">Barter: ${esc(barter.wantedSkill)} ⇄ ${esc(barter.offeredSkill)}</p></div><button type="button" class="gk-icon-btn" data-close aria-label="Tutup">×</button></div>
      <div id="schedule-msg"></div>
      <label class="form-label">Tanggal dan waktu<input class="form-control mt-1" name="at" type="datetime-local" required min="${esc(toLocalInput(Date.now() + 60000))}"></label>
      <label class="form-label">Durasi<select class="form-select mt-1" name="duration" required><option value="${esc(agreement.durationMinutes)}" selected>${esc(agreement.durationMinutes)} menit (sesuai kesepakatan)</option></select></label>
      <div class="d-flex justify-content-end gap-2"><button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button><button class="gk-btn text-white" style="background:var(--gk-primary)">Buat sesi</button></div>
    </form>`;
  dlg.querySelectorAll("[data-close]").forEach((b) => b.onclick = () => dlg.close());
  dlg.querySelector("form").onsubmit = async (ev) => {
    ev.preventDefault();
    const fd = new FormData(ev.target);
    try {
      const session = await api("/api/sessions", { method: "POST", body: {
        barterRequestId: barter.id, scheduledAt: new Date(fd.get("at")).toISOString(), durationMinutes: Number(fd.get("duration"))
      }});
      dlg.close();
      toast("Usulan jadwal dikirim; menunggu konfirmasi partner");
      window.location.hash = `#/sessions/${session.id}`;
    } catch (e) { dlg.querySelector("#schedule-msg").innerHTML = alertBox(e.message); }
  };
  dlg.showModal();
}
