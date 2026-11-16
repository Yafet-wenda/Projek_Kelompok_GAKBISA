import { api, apiBlob } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, toast, alertBox } from "../ui.js";

const MAX_BYTES = 50 * 1024 * 1024; // samakan dengan MAX_FILE_SIZE_BYTES di backend
const ACCEPT = "image/jpeg,image/png,image/webp,video/mp4,video/webm,application/pdf";
const kindOf = (m = "") => (m.startsWith("image/") ? "image" : m.startsWith("video/") ? "video" : "file");
const KIND = { image: "Foto", video: "Video", file: "PDF" };
const initials = (n = "") => n.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join("") || "?";
const fmtSize = (b) => (b >= 1048576 ? (b / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(b / 1024)) + " KB");

export default async function page(view) {
  const uid = currentUser().id;
  const [me, rep, works] = await Promise.all([
    api("/api/me"),
    api(`/api/users/${uid}`).catch(() => null),
    api(`/api/portfolios/user/${uid}`).catch(() => []),
  ]);
  let items = works;
  const urls = new Set();
  const track = (blob) => { const u = URL.createObjectURL(blob); urls.add(u); return u; };
  window.addEventListener("hashchange", () => urls.forEach((u) => URL.revokeObjectURL(u)), { once: true });

  view.innerHTML = `
    <div id="msg" aria-live="polite"></div>
    <section class="gp-glass gp-hero">
      <div class="gp-avatar" aria-hidden="true">${esc(initials(me.fullName))}</div>
      <div class="gp-id">
        <h1 id="gp-name">${esc(me.fullName)}</h1>
        <p id="gp-sub">${esc([me.campus, me.email].filter(Boolean).join(" · "))}</p>
        <p id="gp-bio" class="gp-bio">${esc(me.bio || "Belum ada bio. Ceritakan sedikit tentang keahlian dan tujuan belajarmu.")}</p>
      </div>
      <dl class="gp-stats">
        <div><dt>Rating</dt><dd>${rep?.averageRating == null ? "-" : Number(rep.averageRating).toFixed(1)}</dd></div>
        <div><dt>Ulasan</dt><dd>${esc(rep?.reviewCount ?? 0)}</dd></div>
        <div><dt>Karya</dt><dd id="gp-count">${items.length}</dd></div>
      </dl>
    </section>

    <div class="gp-cols">
      <form id="f" class="gp-glass gp-card">
        <h2>Data diri</h2>
        <label class="gp-field"><span>Email</span><input class="gp-input" value="${esc(me.email)}" disabled></label>
        <label class="gp-field"><span>Nama lengkap</span><input name="fullName" class="gp-input" required maxlength="120" value="${esc(me.fullName)}"></label>
        <label class="gp-field"><span>Kampus</span><input name="campus" class="gp-input" value="${esc(me.campus ?? "")}"></label>
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
            <span id="up-prev" class="gp-prev"><i class="bi bi-cloud-arrow-up"></i><b>Pilih foto, video, atau PDF</b><small>Maks. 50 MB · JPG, PNG, WEBP, MP4, WEBM, PDF</small></span>
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
    <div id="gp-lb" class="gp-lb" hidden></div>`;

  const $ = (s) => view.querySelector(s);
  const showMsg = (m) => { $("#msg").innerHTML = alertBox(m); window.scrollTo({ top: 0, behavior: "smooth" }); };

  // ---------- Galeri ----------
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

  // ---------- Unggah ----------
  const up = $("#up");
  let prevUrl = null;
  const resetUpload = () => {
    up.reset(); up.hidden = true;
    if (prevUrl) { URL.revokeObjectURL(prevUrl); prevUrl = null; }
    $("#up-prev").innerHTML = `<i class="bi bi-cloud-arrow-up"></i><b>Pilih foto, video, atau PDF</b><small>Maks. 50 MB · JPG, PNG, WEBP, MP4, WEBM, PDF</small>`;
  };
  $("#gp-add").onclick = () => { up.hidden = !up.hidden; if (!up.hidden) up.scrollIntoView({ behavior: "smooth", block: "nearest" }); };
  $("#up-cancel").onclick = resetUpload;
  up.elements.file.onchange = () => {
    const f = up.elements.file.files[0], box = $("#up-prev");
    if (prevUrl) { URL.revokeObjectURL(prevUrl); prevUrl = null; }
    if (!f) return;
    if (f.size > MAX_BYTES) { up.elements.file.value = ""; showMsg("Ukuran file melebihi 50 MB."); return; }
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
      if (lbItem !== p) return; // sudah ditutup / ganti karya
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
      $("#gp-sub").textContent = [d.campus, me.email].filter(Boolean).join(" · ");
      $("#gp-bio").textContent = d.bio || "Belum ada bio. Ceritakan sedikit tentang keahlian dan tujuan belajarmu.";
      toast("Profil disimpan");
    } catch (e) { showMsg(e.message); }
  };
}
