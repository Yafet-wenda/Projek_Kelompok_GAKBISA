import { api } from "../api.js";
import { launchGroupMeeting } from "../meeting.js";
import { esc, toast, alertBox, toLocalInput } from "../ui.js";

const levelName = { BEGINNER: "Pemula", INTERMEDIATE: "Menengah", ADVANCED: "Mahir" };

export default async function page(view) {
  let groups = [], skills = [];
  try {
    [groups, skills] = await Promise.all([
      api("/api/groups"), api("/api/skills", { query: { size: 100 } }).then((p) => p.content || []),
    ]);
  } catch (e) {
    view.innerHTML = alertBox(e.message);
    return;
  }
  const groupSessions = new Map();
  await Promise.all(groups.filter((g) => g.myStatus === "ACTIVE").map(async (g) => {
    groupSessions.set(g.id, await api(`/api/groups/${g.id}/sessions`).catch(() => []));
  }));
  const cards = () => groups.length ? groups.map((g) => `
    <article class="gk-card p-3 h-100">
      <div class="d-flex justify-content-between gap-2"><div><h2 class="h6 fw-bold mb-1">${esc(g.name)}</h2><div class="small text-muted">${esc(g.skillName)} · ${esc(levelName[g.level] || g.level)}</div></div><div class="d-flex gap-1 flex-wrap justify-content-end">${g.recommended ? '<span class="gk-badge text-bg-success">Cocok dengan kebutuhanmu</span>' : ""}<span class="gk-badge">${esc(g.visibility === "OPEN" ? "Terbuka" : "Perlu persetujuan")}</span></div></div>
      <p class="small text-muted mt-2 mb-3">Pemilik: ${esc(g.ownerName)} · Anggota: ${esc(g.memberCount)}/${esc(g.maxMembers)}</p>
      ${g.myStatus === "ACTIVE" ? `<div class="small text-success mb-2"><i class="bi bi-check-circle me-1"></i>${g.myRole === "OWNER" ? "Kamu pemilik kelompok" : "Kamu anggota"}</div><div class="d-flex flex-wrap gap-2">${g.myRole === "OWNER" ? `<button class="gk-btn gk-btn--secondary gk-btn--sm" data-action="members" data-id="${esc(g.id)}">Kelola anggota</button><button class="gk-btn gk-btn--secondary gk-btn--sm" data-action="transfer" data-id="${esc(g.id)}">Alihkan pemilik</button><button class="gk-btn gk-btn--danger gk-btn--sm" data-action="close" data-id="${esc(g.id)}">Tutup kelompok</button>` : `<button class="gk-btn gk-btn--secondary gk-btn--sm" data-action="leave" data-id="${esc(g.id)}">Keluar</button>`}<button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)" data-action="chat" data-id="${esc(g.id)}" data-name="${esc(g.name)}">Buka chat</button></div>
        <div class="border-top mt-3 pt-2"><div class="small fw-bold mb-2">Sesi kelompok</div>${(groupSessions.get(g.id) || []).map((s) => `<div class="small d-flex justify-content-between align-items-center gap-2 mb-2"><span>${esc(new Date(s.scheduledAt).toLocaleString("id-ID"))} · ${esc(s.durationMinutes)} mnt · ${esc(s.status)}</span><div class="d-flex gap-1">${s.status === "SCHEDULED" ? `<button class="btn btn-sm btn-outline-primary" data-action="meeting" data-id="${esc(s.id)}">Gabung</button>${g.myRole === "OWNER" ? `<button class="btn btn-sm btn-outline-success" data-action="session-status" data-session="${esc(s.id)}" data-group="${esc(g.id)}" data-status="COMPLETED">Selesai</button><button class="btn btn-sm btn-outline-danger" data-action="session-status" data-session="${esc(s.id)}" data-group="${esc(g.id)}" data-status="CANCELLED">Batalkan</button>` : ""}` : ""}</div></div>`).join("") || '<div class="small text-muted mb-2">Belum ada sesi.</div>'}${g.myRole === "OWNER" ? `<form data-group-session="${esc(g.id)}" class="row g-2"><div class="col-6"><input type="datetime-local" name="at" min="${toLocalInput(Date.now() + 60000)}" class="form-control form-control-sm" required></div><div class="col-3"><select name="duration" class="form-select form-select-sm"><option>30</option><option selected>60</option><option>90</option><option>120</option></select></div><div class="col-3"><button class="btn btn-sm btn-primary">Jadwalkan</button></div></form>` : ""}</div>` : g.myStatus === "PENDING" ? `<span class="small text-warning">Permintaan bergabung menunggu persetujuan</span>` : `<button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)" data-action="join" data-id="${esc(g.id)}">${g.visibility === "OPEN" ? "Gabung" : "Minta bergabung"}</button>`}
    </article>`).join("") : `<div class="gk-card p-4 text-center text-muted">Belum ada kelompok belajar. Buat kelompok pertama atau coba lagi nanti.</div>`;

  view.innerHTML = `
    <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3"><div><h1 class="h4 fw-bold mb-1">Kelompok belajar</h1><p class="small text-muted mb-0">Belajar bersama mahasiswa lain berdasarkan topik dan level.</p></div></div>
    <div id="groups-msg"></div>
    <details class="gk-card p-3 mb-3"><summary class="fw-bold">Buat kelompok belajar</summary>
      <form id="create-group" class="row g-2 mt-2">
        <div class="col-md-6"><label class="form-label small">Nama kelompok<input class="form-control" name="name" maxlength="120" required></label></div>
        <div class="col-md-6"><label class="form-label small">Topik<select class="form-select" name="skillId" required><option value="">Pilih keahlian</option>${skills.map((s) => `<option value="${esc(s.id)}">${esc(s.name)} · ${esc(s.category)}</option>`).join("")}</select></label></div>
        <div class="col-md-4"><label class="form-label small">Level<select class="form-select" name="level"><option value="BEGINNER">Pemula</option><option value="INTERMEDIATE" selected>Menengah</option><option value="ADVANCED">Mahir</option></select></label></div>
        <div class="col-md-4"><label class="form-label small">Kapasitas<input class="form-control" type="number" name="maxMembers" min="2" max="100" value="8" required></label></div>
        <div class="col-md-4"><label class="form-label small">Visibilitas<select class="form-select" name="visibility"><option value="OPEN">Terbuka</option><option value="INVITE">Perlu persetujuan</option></select></label></div>
        <div class="col-12"><button class="gk-btn text-white" style="background:var(--gk-primary)">Buat kelompok</button></div>
      </form>
    </details>
    <div class="row g-3">${cards()}</div>`;

  view.querySelector("#create-group").onsubmit = async (ev) => {
    ev.preventDefault(); const d = Object.fromEntries(new FormData(ev.target));
    try { await api("/api/groups", { method: "POST", body: { ...d, maxMembers: Number(d.maxMembers) } }); toast("Kelompok berhasil dibuat"); await page(view); }
    catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
  };
  view.querySelectorAll("form[data-group-session]").forEach((form) => form.onsubmit = async (ev) => {
    ev.preventDefault(); const data = new FormData(form);
    try { const s = await api(`/api/groups/${form.dataset.groupSession}/sessions`, { method: "POST", body: { scheduledAt: new Date(data.get("at")).toISOString(), durationMinutes: Number(data.get("duration")) } }); toast("Sesi kelompok dijadwalkan"); window.open(s.meetingUrl, "_blank", "noopener"); await page(view); }
    catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
  });
  view.onclick = async (ev) => {
    const sessionStatusBtn = ev.target.closest("button[data-action=session-status]");
    if (sessionStatusBtn) {
      const status = sessionStatusBtn.dataset.status;
      try { await api(`/api/groups/${sessionStatusBtn.dataset.group}/sessions/${sessionStatusBtn.dataset.session}/status`, { method: "POST", body: { status } }); toast(status === "COMPLETED" ? "Sesi ditandai selesai" : "Sesi dibatalkan"); await page(view); }
      catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
      return;
    }
    const transferBtn = ev.target.closest("button[data-action=transfer]");
    if (transferBtn) {
      try {
        const members = (await api(`/api/groups/${transferBtn.dataset.id}/members`)).filter((m) => m.status === "ACTIVE" && m.role !== "OWNER");
        if (!members.length) { view.querySelector("#groups-msg").innerHTML = alertBox("Belum ada anggota aktif untuk menerima kepemilikan.", "warning"); return; }
        const dlg = document.createElement("dialog"); dlg.className = "gk-dialog";
        dlg.innerHTML = `<form class="gk-card p-4"><h2 class="h5 fw-bold">Alihkan kepemilikan</h2><p class="small text-muted">Kamu akan menjadi anggota biasa setelah pengalihan.</p><select class="form-select mb-3" name="newOwnerId">${members.map((m) => `<option value="${esc(m.userId)}">${esc(m.fullName)}</option>`).join("")}</select><div class="d-flex justify-content-end gap-2"><button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button><button class="gk-btn text-white" style="background:var(--gk-primary)">Alihkan</button></div></form>`;
        view.append(dlg); dlg.showModal(); dlg.querySelector("[data-close]").onclick = () => dlg.close(); dlg.addEventListener("close", () => dlg.remove(), { once: true });
        dlg.querySelector("form").onsubmit = async (event) => { event.preventDefault(); const newOwnerId = new FormData(event.currentTarget).get("newOwnerId"); try { await api(`/api/groups/${transferBtn.dataset.id}/transfer`, { method: "POST", body: { newOwnerId } }); dlg.close(); toast("Kepemilikan kelompok dialihkan"); await page(view); } catch (e) { dlg.querySelector("form").insertAdjacentHTML("afterbegin", alertBox(e.message)); } };
      } catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
      return;
    }
    const closeBtn = ev.target.closest("button[data-action=close]");
    if (closeBtn) {
      if (!confirm("Tutup kelompok? Sesi terjadwal yang belum dimulai akan dibatalkan.")) return;
      try { await api(`/api/groups/${closeBtn.dataset.id}/close`, { method: "POST" }); toast("Kelompok ditutup"); await page(view); }
      catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
      return;
    }
    const meetingBtn = ev.target.closest("button[data-action=meeting]");
    if (meetingBtn) {
      await launchGroupMeeting(view, meetingBtn.dataset.id, view.querySelector("#groups-msg"));
      return;
    }
    const chatBtn = ev.target.closest("button[data-action=chat]");
    if (chatBtn) {
      const groupId = chatBtn.dataset.id;
      const dlg = document.createElement("dialog"); dlg.className = "gk-dialog";
      dlg.innerHTML = `<section class="gk-card p-3" style="width:min(700px,90vw)"><div class="d-flex justify-content-between align-items-center"><h2 class="h5 fw-bold">Chat · ${esc(chatBtn.dataset.name)}</h2><button class="gk-icon-btn" data-close>Tutup</button></div><div id="group-chat-messages" class="gk-chat-msgs" style="height:55vh"></div><div id="group-chat-error"></div><form class="d-flex gap-2 mt-2"><input class="form-control" name="message" maxlength="2000" required placeholder="Tulis pesan…"><button class="gk-btn text-white" style="background:var(--gk-primary)">Kirim</button></form></section>`;
      view.append(dlg); dlg.showModal();
      const close = () => dlg.close(); dlg.querySelector("[data-close]").onclick = close;
      const box = dlg.querySelector("#group-chat-messages"); let prev = -1;
      const load = async () => {
        try {
          const messages = await api(`/api/groups/${groupId}/chat`);
          if (messages.length !== prev) { prev = messages.length; box.innerHTML = messages.map((m) => `<div class="gk-chat-row ${m.isMe ? "gk-chat-row--me" : "gk-chat-row--partner"}"><div class="gk-chat-bubble ${m.isMe ? "gk-chat-bubble--me" : "gk-chat-bubble--partner"}">${!m.isMe ? `<div class="gk-chat-sender">${esc(m.senderName)}</div>` : ""}<div class="gk-chat-text">${esc(m.content)}</div><div class="gk-chat-time">${esc(new Date(m.createdAt).toLocaleString("id-ID"))}</div><button class="btn btn-link btn-sm p-0" data-chat-action="${m.isMe?"delete":"report"}" data-message="${esc(m.id)}">${m.isMe?"Hapus":"Laporkan"}</button></div></div>`).join("") || '<div class="gk-chat-empty">Belum ada pesan.</div>'; box.scrollTop = box.scrollHeight; }
        } catch (e) { dlg.querySelector("#group-chat-error").innerHTML = alertBox(e.message); }
      };
      await load(); const timer = setInterval(load, 8000);
      box.onclick = async (event) => { const action=event.target.closest("[data-chat-action]"); if(!action)return; try { if(action.dataset.chatAction==="delete") await api(`/api/groups/${groupId}/chat/${action.dataset.message}`,{method:"DELETE"}); else {const reason=prompt("Alasan laporan (minimal 5 karakter)");if(reason===null)return;await api(`/api/groups/${groupId}/chat/${action.dataset.message}/report`,{method:"POST",body:{reason}});} prev=-1;await load();toast("Tindakan pada pesan berhasil"); } catch(e){dlg.querySelector("#group-chat-error").innerHTML=alertBox(e.message);} };
      dlg.addEventListener("close", () => { clearInterval(timer); dlg.remove(); }, { once: true });
      dlg.querySelector("form").onsubmit = async (event) => { event.preventDefault(); const input = event.currentTarget.elements.message; try { await api(`/api/groups/${groupId}/chat`, { method: "POST", body: { message: input.value } }); input.value = ""; await load(); } catch (e) { dlg.querySelector("#group-chat-error").innerHTML = alertBox(e.message); } };
      return;
    }
    const memberAction = ev.target.closest("button[data-action=members]");
    if (memberAction) {
      try {
        const members = await api(`/api/groups/${memberAction.dataset.id}/members`);
        const pending = members.filter((m) => m.status === "PENDING");
        const dlg = document.createElement("dialog"); dlg.className = "gk-dialog";
        dlg.innerHTML = `<div class="gk-card p-4"><div class="d-flex justify-content-between mb-3"><h2 class="h5 fw-bold">Anggota kelompok</h2><button class="gk-icon-btn" data-close>Tutup</button></div>${members.map((m) => `<div class="d-flex justify-content-between border-top py-2"><span>${esc(m.fullName)} <small class="text-muted">${esc(m.role)} · ${esc(m.status)}</small></span>${m.status === "PENDING" ? `<button class="btn btn-sm btn-primary" data-approve="${esc(m.userId)}">Setujui</button>` : ""}</div>`).join("") || "Belum ada anggota."}</div>`;
        view.append(dlg); dlg.showModal();
        dlg.querySelector("[data-close]").onclick = () => dlg.close();
        dlg.addEventListener("close", () => dlg.remove(), { once: true });
        dlg.querySelectorAll("[data-approve]").forEach((button) => button.onclick = async () => {
          try { await api(`/api/groups/${memberAction.dataset.id}/members/${button.dataset.approve}/approve`, { method: "POST" }); toast("Anggota disetujui"); dlg.close(); await page(view); }
          catch (e) { dlg.querySelector(".gk-card").insertAdjacentHTML("afterbegin", alertBox(e.message)); }
        });
      } catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
      return;
    }
    const b = ev.target.closest("button[data-action]"); if (!b) return;
    try { await api(`/api/groups/${b.dataset.id}/${b.dataset.action}`, { method: "POST" }); toast(b.dataset.action === "join" ? "Permintaan bergabung dikirim" : "Kamu keluar dari kelompok"); await page(view); }
    catch (e) { view.querySelector("#groups-msg").innerHTML = alertBox(e.message); }
  };
}
