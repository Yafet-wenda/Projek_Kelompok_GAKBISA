import { api, apiBlob } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, toast, alertBox } from "../ui.js";

const MAX_BYTES = 10 * 1024 * 1024; // sesuai batas PRD dan backend
const ACCEPT = "image/jpeg,image/png,image/webp,application/pdf";
const kindOf = (m = "") => (m.startsWith("image/") ? "image" : m.startsWith("video/") ? "video" : "file");
const KIND = { image: "Foto", video: "Video", file: "PDF" };
const initials = (n = "") => n.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join("") || "?";
const fmtSize = (b) => (b >= 1048576 ? (b / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(b / 1024)) + " KB");

export default async function page(view) {
  const uid = currentUser().id;
  const [me, rep, works, mySkills, catalogData, verification, sessions] = await Promise.all([
    api("/api/me"),
    api(`/api/users/${uid}`).catch(() => null),
    api(`/api/portfolios/user/${uid}`).catch(() => []),
    api("/api/me/skills").catch(() => []),
    api("/api/skills", { query: { size: 100 } }).catch(() => ({ content: [] })),
    api("/api/me/campus-verification").catch(() => ({ status: "NONE", verified: false })),
    api("/api/sessions").catch(() => []),
  ]);

  let items = works;
  let skills = mySkills;
  const catalog = catalogData.content || [];

  const urls = new Set();
  const track = (blob) => { const u = URL.createObjectURL(blob); urls.add(u); return u; };
  window.addEventListener("hashchange", () => urls.forEach((u) => URL.revokeObjectURL(u)), { once: true });

  view.innerHTML = `
    <div id="msg" aria-live="polite"></div>
    <section class="gp-glass gp-hero">
      <div class="gp-avatar" aria-hidden="true">${me.avatarPath ? `<img src="${esc(me.avatarPath)}" alt="" class="w-100 h-100 rounded-circle object-fit-cover">` : esc(initials(me.fullName))}</div>
      <div class="gp-id">
        <h1 id="gp-name">${esc(me.fullName)} ${me.verified ? '<span class="badge bg-success align-middle" title="Email kampus telah diverifikasi">Terverifikasi</span>' : ""}</h1>
        <p id="gp-sub">${esc([me.campus, me.studyProgram, me.email].filter(Boolean).join(" · "))}</p>
        <p id="gp-bio" class="gp-bio">${esc(me.bio || "Belum ada bio. Ceritakan sedikit tentang keahlian dan tujuan belajarmu.")}</p>
      </div>
      <dl class="gp-stats">
        <div><dt>Rating</dt><dd>${rep?.averageRating == null ? "-" : Number(rep.averageRating).toFixed(1)}</dd></div>
        <div><dt>Ulasan</dt><dd>${esc(rep?.reviewCount ?? 0)}</dd></div>
        <div><dt>Sesi selesai</dt><dd>${sessions.filter((s) => s.status === "COMPLETED").length}</dd></div>
        <div><dt>Karya</dt><dd id="gp-count">${items.length}</dd></div>
      </dl>
    </section>

    <section class="gk-card p-3 mb-4"><div class="d-flex justify-content-between align-items-center flex-wrap gap-2"><div><h2 class="h6 fw-bold mb-1">Verifikasi email kampus</h2><p class="small text-muted mb-0">Status: ${me.verified ? "Terverifikasi" : esc(verification.status || "Belum diajukan")}${verification.campusEmail ? ` · ${esc(verification.campusEmail)}` : ""}. Kode dikirim ke email kampus dan berlaku 15 menit.</p></div>${!me.verified ? `<form id="campus-verification-form" class="d-flex gap-2"><input class="form-control" type="email" name="campusEmail" placeholder="nama@kampus.ac.id" required><button class="gk-btn gk-btn--secondary">Kirim kode</button></form>` : ""}</div>${!me.verified && verification.status === "PENDING" ? `<form id="campus-code-form" class="d-flex gap-2 mt-3"><input class="form-control" name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" placeholder="Kode 6 digit" required><button class="gk-btn text-white" style="background:var(--gk-primary)">Verifikasi</button></form>` : ""}<div id="campus-msg"></div></section>

    <!-- Bagian Manajemen Keahlian (OFFER & WANT) -->
    <section class="gp-glass gp-card mb-4" id="gp-skills-card">
      <header class="gp-head">
        <div>
          <h2>Keahlian &amp; Minat Belajar</h2>
          <p>Tentukan keahlian yang kamu kuasai (OFFER) dan ingin kamu pelajari (WANT) untuk pencocokan partner.</p>
        </div>
        <button id="skill-add-btn" class="gp-btn gp-btn--pri" type="button">
          <i class="bi bi-plus-lg"></i> Tambah keahlian
        </button>
      </header>

      <form id="skill-form" class="gp-up" hidden>
        <div class="row g-2">
          <div class="col-md-4">
            <label class="gp-field">
              <span>Tipe keahlian</span>
              <select name="type" class="gp-input" required>
                <option value="OFFER">Saya tawarkan / ajarkan (OFFER)</option>
                <option value="WANT">Ingin saya pelajari (WANT)</option>
              </select>
            </label>
          </div>
          <div class="col-md-4">
            <label class="gp-field">
              <span>Tingkat kemahiran</span>
              <select name="level" class="gp-input" required>
                <option value="BEGINNER">Pemula (BEGINNER)</option>
                <option value="INTERMEDIATE" selected>Menengah (INTERMEDIATE)</option>
                <option value="ADVANCED">Mahir (ADVANCED)</option>
              </select>
            </label>
          </div>
          <div class="col-md-4">
            <label class="gp-field">
              <span>Pilih dari katalog</span>
              <select name="skillId" class="gp-input">
                <option value="">— Atau ketik nama di bawah —</option>
                ${catalog.map((s) => `<option value="${esc(s.id)}">${esc(s.name)} (${esc(s.category)})</option>`).join("")}
              </select>
            </label>
          </div>
          <div class="col-md-6">
            <label class="gp-field">
              <span>Nama keahlian baru (jika tidak ada di katalog)</span>
              <input name="skillName" class="gp-input" placeholder="Contoh: Figma UI/UX, Flutter, Python…">
            </label>
          </div>
          <div class="col-md-6">
            <label class="gp-field">
              <span>Catatan / fokus spesifik (opsional)</span>
              <input name="notes" class="gp-input" placeholder="Contoh: Fokus ke state management, backend API…">
            </label>
          </div>
        </div>
        <div class="gp-row mt-2">
          <button class="gp-btn gp-btn--pri" type="submit">Simpan keahlian</button>
          <button type="button" id="skill-cancel-btn" class="gp-btn">Batal</button>
        </div>
      </form>

      <div class="row g-3" id="skills-display">
        <div class="col-md-6">
          <div class="p-3 rounded-4" style="background:rgba(29,95,232,0.06);border:1px solid rgba(29,95,232,0.15)">
            <div class="d-flex align-items-center justify-content-between mb-2">
              <span class="fw-bold" style="color:var(--gk-primary)"><i class="bi bi-award me-1"></i>Keahlian yang Ditawarkan (OFFER)</span>
              <span class="badge bg-primary rounded-pill" id="offer-count">0</span>
            </div>
            <div id="offer-list" class="d-flex flex-column gap-2"></div>
          </div>
        </div>
        <div class="col-md-6">
          <div class="p-3 rounded-4" style="background:rgba(249,115,22,0.06);border:1px solid rgba(249,115,22,0.15)">
            <div class="d-flex align-items-center justify-content-between mb-2">
              <span class="fw-bold" style="color:#f97316"><i class="bi bi-lightbulb me-1"></i>Ingin Dipelajari (WANT)</span>
              <span class="badge bg-warning text-dark rounded-pill" id="want-count">0</span>
            </div>
            <div id="want-list" class="d-flex flex-column gap-2"></div>
          </div>
        </div>
      </div>
    </section>

    <div class="gp-cols">
      <form id="f" class="gp-glass gp-card">
        <h2>Data diri</h2>
        <label class="gp-field"><span>Email</span><input class="gp-input" value="${esc(me.email)}" disabled></label>
        <label class="gp-field"><span>Nama lengkap</span><input name="fullName" class="gp-input" required maxlength="120" value="${esc(me.fullName)}"></label>
        <label class="gp-field"><span>Kampus</span><input name="campus" class="gp-input" value="${esc(me.campus ?? "")}"></label>
        <label class="gp-field"><span>Program studi</span><input name="studyProgram" class="gp-input" maxlength="150" value="${esc(me.studyProgram ?? "")}"></label>
        <label class="gp-field"><span>Minat</span><input name="interests" class="gp-input" maxlength="500" value="${esc(me.interests ?? "")}"></label>
        <label class="gp-field"><span>URL foto profil (HTTPS)</span><input name="avatarPath" type="url" class="gp-input" value="${esc(me.avatarPath ?? "")}" placeholder="https://..."></label>
        <label class="gp-field"><span>Bio</span><textarea name="bio" rows="4" class="gp-input">${esc(me.bio ?? "")}</textarea></label>
        <button class="gp-btn gp-btn--pri">Simpan perubahan</button>
      </form>

      <section class="gp-glass gp-card">
        <header class="gp-head">
          <div><h2>Portofolio</h2><p>Foto, video, atau PDF karyamu.</p></div>
          <button id="gp-add" class="gp-btn gp-btn--pri" type="button"><i class="bi bi-plus-lg"></i> Tambah karya</button>
        </header>

        <form id="up" class="gp-up" hidden>
          <label class="gp-drop">
            <input name="file" type="file" accept="${ACCEPT}" required>
            <span id="up-prev" class="gp-prev"><i class="bi bi-cloud-arrow-up"></i><b>Pilih foto atau PDF</b><small>Maks. 10 MB · JPG, PNG, WEBP, PDF</small></span>
          </label>
          <label class="gp-field"><span>Judul</span><input name="title" class="gp-input" required maxlength="150"></label>
          <label class="gp-field"><span>Deskripsi (opsional)</span><textarea name="description" rows="2" class="gp-input"></textarea></label>
          <label class="gp-field"><span>Visibilitas</span>
            <select name="visibility" class="gp-input"><option value="PUBLIC">Publik, terlihat semua orang</option><option value="PRIVATE">Privat, hanya aku</option></select></label>
          <div class="gp-row"><button class="gp-btn gp-btn--pri">Unggah</button><button type="button" id="up-cancel" class="gp-btn">Batal</button></div>
        </form>

        <div id="gp-grid" class="gp-grid"></div>
      </section>
    </div>
    <div id="gp-lb" class="gp-lb" hidden></div>
    <dialog id="skill-edit-dialog" class="gk-dialog"></dialog>`;

  const $ = (s) => view.querySelector(s);
  const showMsg = (m) => { $("#msg").innerHTML = alertBox(m); window.scrollTo({ top: 0, behavior: "smooth" }); };

  $("#campus-verification-form")?.addEventListener("submit", async (ev) => {
    ev.preventDefault(); const campusEmail = new FormData(ev.currentTarget).get("campusEmail");
    try { await api("/api/me/campus-verification", { method: "POST", body: { campusEmail } }); toast("Kode verifikasi dikirim ke email kampus"); await page(view); }
    catch (e) { showMsg(e.message); }
  });
  $("#campus-code-form")?.addEventListener("submit", async (ev) => { ev.preventDefault(); const code=new FormData(ev.currentTarget).get("code"); try { await api("/api/me/campus-verification/confirm",{method:"POST",body:{code}}); toast("Email kampus berhasil diverifikasi"); await page(view); } catch(e) { $("#campus-msg").innerHTML=alertBox(e.message); } });

  // ---------- Render Keahlian ----------
  const offerList = $("#offer-list");
  const wantList = $("#want-list");
  const offerCount = $("#offer-count");
  const wantCount = $("#want-count");

  const levelBadge = (lvl) => {
    const map = {
      BEGINNER: { label: "Pemula", cls: "bg-secondary" },
      INTERMEDIATE: { label: "Menengah", cls: "bg-info text-dark" },
      ADVANCED: { label: "Mahir", cls: "bg-success" },
    };
    const x = map[lvl] || { label: lvl, cls: "bg-secondary" };
    return `<span class="badge ${x.cls}" style="font-size:11px">${esc(x.label)}</span>`;
  };

  function renderSkills() {
    const offers = skills.filter((s) => s.type === "OFFER");
    const wants = skills.filter((s) => s.type === "WANT");

    offerCount.textContent = offers.length;
    wantCount.textContent = wants.length;

    const renderItem = (s) => `
      <div class="p-2 rounded-3 d-flex justify-content-between align-items-center gap-2"
           style="background:var(--gk-card-bg);border:1px solid var(--gk-border)">
        <div class="min-w-0">
          <div class="d-flex align-items-center gap-2 flex-wrap">
            <span class="fw-bold" style="color:var(--gk-ink)">${esc(s.skillName)}</span>
            ${levelBadge(s.level)}
            ${s.category ? `<span class="small text-muted">(${esc(s.category)})</span>` : ""}
          </div>
          ${s.notes ? `<div class="small text-muted text-truncate">${esc(s.notes)}</div>` : ""}
        </div>
        <button type="button" class="btn btn-sm text-danger p-1" data-del-skill="${esc(s.id)}"
                title="Hapus keahlian" aria-label="Hapus keahlian">
          <i class="bi bi-trash"></i>
        </button><button type="button" class="btn btn-sm text-primary p-1" data-edit-skill="${esc(s.id)}" title="Ubah keahlian" aria-label="Ubah keahlian"><i class="bi bi-pencil"></i></button>
      </div>`;

    offerList.innerHTML = offers.length
      ? offers.map(renderItem).join("")
      : '<p class="small text-muted m-0 py-2">Belum ada keahlian yang kamu tawarkan.</p>';

    wantList.innerHTML = wants.length
      ? wants.map(renderItem).join("")
      : '<p class="small text-muted m-0 py-2">Belum ada keahlian yang ingin kamu pelajari.</p>';
  }
  renderSkills();

  // Tambah & Hapus Keahlian
  const skillForm = $("#skill-form");
  $("#skill-add-btn").onclick = () => {
    skillForm.hidden = !skillForm.hidden;
    if (!skillForm.hidden) skillForm.scrollIntoView({ behavior: "smooth", block: "nearest" });
  };
  $("#skill-cancel-btn").onclick = () => {
    skillForm.reset();
    skillForm.hidden = true;
  };

  skillForm.onsubmit = async (ev) => {
    ev.preventDefault();
    const fd = new FormData(skillForm);
    const type = fd.get("type");
    const level = fd.get("level");
    const skillId = fd.get("skillId") || null;
    const skillName = fd.get("skillName") ? fd.get("skillName").trim() : null;
    const notes = fd.get("notes") ? fd.get("notes").trim() : null;

    if (!skillId && !skillName) {
      showMsg("Pilih keahlian dari katalog atau ketik nama keahlian baru.");
      return;
    }

    try {
      const added = await api("/api/me/skills", {
        method: "POST",
        body: { skillId, skillName, type, level, notes },
      });
      skills.push(added);
      renderSkills();
      skillForm.reset();
      skillForm.hidden = true;
      toast("Keahlian berhasil ditambahkan");
    } catch (e) {
      showMsg(e.message);
    }
  };

  $("#gp-skills-card").onclick = async (ev) => {
    const edit = ev.target.closest("button[data-edit-skill]");
    if (edit) {
      const skill = skills.find((s) => String(s.id) === edit.dataset.editSkill);
      const dlg = $("#skill-edit-dialog");
      dlg.innerHTML = `<form class="gk-card p-4 d-flex flex-column gap-3"><h2 class="h5 fw-bold">Ubah ${esc(skill.skillName)}</h2><label class="form-label">Level<select name="level" class="form-select"><option value="BEGINNER" ${skill.level === "BEGINNER" ? "selected" : ""}>Pemula</option><option value="INTERMEDIATE" ${skill.level === "INTERMEDIATE" ? "selected" : ""}>Menengah</option><option value="ADVANCED" ${skill.level === "ADVANCED" ? "selected" : ""}>Mahir</option></select></label><label class="form-label">Catatan<input name="notes" class="form-control" maxlength="500" value="${esc(skill.notes || "")}"></label><div class="d-flex justify-content-end gap-2"><button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button><button class="gk-btn gp-btn--pri">Simpan</button></div></form>`;
      dlg.querySelector("[data-close]").onclick = () => dlg.close();
      dlg.querySelector("form").onsubmit = async (event) => {
        event.preventDefault(); const d = Object.fromEntries(new FormData(event.currentTarget));
        try { const updated = await api(`/api/me/skills/${skill.id}`, { method: "PUT", body: d }); Object.assign(skill, updated); renderSkills(); dlg.close(); toast("Keahlian diperbarui"); }
        catch (e) { showMsg(e.message); }
      };
      dlg.showModal();
      return;
    }
    const btn = ev.target.closest("button[data-del-skill]");
    if (!btn) return;
    const id = btn.dataset.delSkill;
    if (!confirm("Hapus keahlian ini dari profilmu?")) return;
    try {
      await api(`/api/me/skills/${id}`, { method: "DELETE" });
      skills = skills.filter((s) => s.id !== id);
      renderSkills();
      toast("Keahlian dihapus");
    } catch (e) {
      showMsg(e.message);
    }
  };

  // ---------- Galeri Portofolio ----------
  const grid = $("#gp-grid");
  const thumbIo = "IntersectionObserver" in window
    ? new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { thumbIo.unobserve(e.target); loadThumb(e.target); } }), { rootMargin: "200px" })
    : null;
  async function loadThumb(img) {
    try { img.src = track(await apiBlob(`/api/portfolios/${img.dataset.id}/content`)); img.classList.add("is-in"); }
    catch { img.closest(".gp-thumb").classList.add("is-broken"); }
  }
  const tile = (p) => {
    const k = kindOf(p.mimeType);
    const inner = k === "image" ? `<img data-id="${esc(p.id)}" alt="">`
      : k === "video" ? `<i class="bi bi-play-circle-fill"></i><em>VIDEO</em>`
      : `<i class="bi bi-file-earmark-pdf"></i><em>PDF</em>`;
    return `<button type="button" class="gp-tile" data-id="${esc(p.id)}" aria-label="Buka ${esc(p.title)}">
      <span class="gp-thumb gp-thumb--${k}">${inner}</span>
      <span class="gp-cap"><b>${esc(p.title)}</b><small>${KIND[k]} · ${fmtSize(p.fileSize)}${p.visibility === "PRIVATE" ? ' · <i class="bi bi-lock-fill"></i> Privat' : ""}</small></span></button>`;
  };
  function renderGrid() {
    $("#gp-count").textContent = items.length;
    grid.innerHTML = items.length ? items.map(tile).join("")
      : `<div class="gp-empty"><i class="bi bi-images"></i><b>Belum ada karya</b><p>Tambahkan foto, video, atau PDF supaya calon partner bisa melihat kemampuanmu.</p></div>`;
    grid.querySelectorAll("img[data-id]").forEach((img) => (thumbIo ? thumbIo.observe(img) : loadThumb(img)));
  }
  renderGrid();

  // ---------- Unggah Portofolio ----------
  const up = $("#up");
  let prevUrl = null;
  const resetUpload = () => {
    up.reset(); up.hidden = true;
    if (prevUrl) { URL.revokeObjectURL(prevUrl); prevUrl = null; }
    $("#up-prev").innerHTML = `<i class="bi bi-cloud-arrow-up"></i><b>Pilih foto atau PDF</b><small>Maks. 10 MB · JPG, PNG, WEBP, PDF</small>`;
  };
  $("#gp-add").onclick = () => { up.hidden = !up.hidden; if (!up.hidden) up.scrollIntoView({ behavior: "smooth", block: "nearest" }); };
  $("#up-cancel").onclick = resetUpload;
  up.elements.file.onchange = () => {
    const f = up.elements.file.files[0], box = $("#up-prev");
    if (prevUrl) { URL.revokeObjectURL(prevUrl); prevUrl = null; }
    if (!f) return;
    if (f.size > MAX_BYTES) { up.elements.file.value = ""; showMsg("Ukuran file melebihi 10 MB."); return; }
    if (!up.elements.title.value) up.elements.title.value = f.name.replace(/\.[^.]+$/, "").slice(0, 150);
    const k = kindOf(f.type);
    if (k === "file") { box.innerHTML = `<i class="bi bi-file-earmark-pdf"></i><b>${esc(f.name)}</b><small>${fmtSize(f.size)}</small>`; return; }
    prevUrl = URL.createObjectURL(f);
    box.innerHTML = k === "image" ? `<img src="${prevUrl}" alt="Pratinjau">` : `<video src="${prevUrl}" muted playsinline controls></video>`;
  };
  up.onsubmit = async (ev) => {
    ev.preventDefault();
    const btn = up.querySelector(".gp-btn--pri");
    btn.disabled = true; btn.textContent = "Mengunggah…";
    try {
      items.unshift(await api("/api/portfolios", { method: "POST", form: new FormData(up) }));
      renderGrid(); resetUpload(); toast("Karya ditambahkan");
    } catch (e) { showMsg(e.message); }
    finally { btn.disabled = false; btn.textContent = "Unggah"; }
  };

  // ---------- Lightbox ----------
  const lb = $("#gp-lb");
  let lbUrl = null, lbItem = null;
  const onKey = (e) => e.key === "Escape" && closeLb();
  function closeLb() {
    lb.hidden = true; lb.innerHTML = ""; lbItem = null;
    if (lbUrl) { URL.revokeObjectURL(lbUrl); urls.delete(lbUrl); lbUrl = null; }
    document.removeEventListener("keydown", onKey);
  }
  const lbInfo = (p) => `<h3>${esc(p.title)}</h3>${p.description ? `<p>${esc(p.description)}</p>` : ""}
    <small>${esc(p.fileName)} · ${fmtSize(p.fileSize)} · ${p.visibility === "PRIVATE" ? "Privat" : "Publik"}</small>
    <div class="gp-row"><button class="gp-btn" data-act="vis">${p.visibility === "PRIVATE" ? "Jadikan publik" : "Jadikan privat"}</button>
    <button class="gp-btn gp-btn--del" data-act="del"><i class="bi bi-trash"></i> Hapus</button></div>`;
  async function openLb(p) {
    lbItem = p;
    lb.hidden = false;
    lb.innerHTML = `<div class="gp-lb__box gp-glass" role="dialog" aria-modal="true" aria-label="${esc(p.title)}">
      <button class="gp-lb__x" data-act="close" aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
      <div class="gp-lb__media"><span class="gp-spin"></span></div><div class="gp-lb__info">${lbInfo(p)}</div></div>`;
    document.addEventListener("keydown", onKey);
    lb.querySelector("[data-act=close]").focus();
    const media = lb.querySelector(".gp-lb__media");
    try {
      const blob = await apiBlob(`/api/portfolios/${p.id}/content`);
      if (lbItem !== p) return;
      lbUrl = track(blob);
      const k = kindOf(p.mimeType);
      media.innerHTML = k === "image" ? `<img src="${lbUrl}" alt="${esc(p.title)}">`
        : k === "video" ? `<video src="${lbUrl}" controls autoplay playsinline></video>`
        : `<div class="gp-pdf"><i class="bi bi-file-earmark-pdf"></i><a class="gp-btn gp-btn--pri" href="${lbUrl}" target="_blank" rel="noopener">Buka PDF</a></div>`;
    } catch (e) { media.innerHTML = `<p class="gp-err">${esc(e.message)}</p>`; }
  }
  grid.onclick = (ev) => {
    const t = ev.target.closest(".gp-tile");
    if (t) openLb(items.find((x) => String(x.id) === t.dataset.id));
  };
  lb.onclick = async (ev) => {
    if (ev.target === lb) return closeLb();
    const act = ev.target.closest("[data-act]")?.dataset.act;
    if (!act || !lbItem) return;
    if (act === "close") return closeLb();
    const p = lbItem;
    try {
      if (act === "del") {
        if (!confirm(`Hapus karya “${p.title}”? Tindakan ini tidak bisa dibatalkan.`)) return;
        await api(`/api/portfolios/${p.id}`, { method: "DELETE" });
        items = items.filter((x) => x.id !== p.id); closeLb(); renderGrid(); toast("Karya dihapus");
      } else if (act === "vis") {
        const next = p.visibility === "PRIVATE" ? "PUBLIC" : "PRIVATE";
        const upd = await api(`/api/portfolios/${p.id}`, { method: "PUT", body: { title: p.title, description: p.description, visibility: next } });
        Object.assign(p, upd); lb.querySelector(".gp-lb__info").innerHTML = lbInfo(p); renderGrid();
      }
    } catch (e) { showMsg(e.message); }
  };

  // ---------- Simpan data diri ----------
  $("#f").onsubmit = async (ev) => {
    ev.preventDefault();
    const d = Object.fromEntries(new FormData(ev.target));
    try {
      await api("/api/me", { method: "PUT", body: d });
      $("#gp-name").textContent = d.fullName;
      $("#gp-sub").textContent = [d.campus, d.studyProgram, me.email].filter(Boolean).join(" · ");
      $("#gp-bio").textContent = d.bio || "Belum ada bio. Ceritakan sedikit tentang keahlian dan tujuan belajarmu.";
      const avatar = view.querySelector(".gp-avatar");
      avatar.innerHTML = d.avatarPath ? `<img src="${esc(d.avatarPath)}" alt="" class="w-100 h-100 rounded-circle object-fit-cover">` : esc(initials(d.fullName));
      toast("Profil disimpan");
    } catch (e) { showMsg(e.message); }
  };
}
