import { api, apiBlob } from "../api.js";
import { currentUser } from "../auth.js";
import { esc, toast, alertBox } from "../ui.js";

const MAX_BYTES = 50 * 1024 * 1024;
const ACCEPT = "image/jpeg,image/png,image/webp,video/mp4,video/webm,application/pdf";
const kindOf = (mime = "") => mime.startsWith("image/") ? "image" : mime.startsWith("video/") ? "video" : "file";
const fmtSize = (bytes) => bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;

export default async function page(view) {
  const [me, rep] = await Promise.all([
    api("/api/me"),
    api(`/api/users/${currentUser().id}`).catch(() => null),
  ]);
  if (!view.isConnected) return;

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3">Profil saya</h1>
    ${rep ? `<p class="small text-muted">Rating rata-rata: <b>${rep.averageRating == null ? "-" : Number(rep.averageRating).toFixed(1)}</b>
      · ${esc(rep.reviewCount)} ulasan · <span id="work-count">${esc(rep.portfolioCount)}</span> karya</p>` : ""}
    <div id="msg" aria-live="polite"></div>
    <form id="f" class="gk-card p-4 d-flex flex-column gap-3">
      <div><label for="profile-email" class="form-label small fw-bold">Email</label><input id="profile-email" class="form-control" value="${esc(me.email)}" disabled></div>
      <div><label for="profile-name" class="form-label small fw-bold">Nama lengkap</label><input id="profile-name" name="fullName" class="form-control" required maxlength="120" autocomplete="name" value="${esc(me.fullName)}"></div>
      <div><label for="profile-campus" class="form-label small fw-bold">Kampus</label><input id="profile-campus" name="campus" class="form-control" maxlength="150" autocomplete="organization" value="${esc(me.campus ?? "")}"></div>
      <div><label for="profile-bio" class="form-label small fw-bold">Bio</label><textarea id="profile-bio" name="bio" rows="3" class="form-control">${esc(me.bio ?? "")}</textarea></div>
      <button class="gk-btn gk-btn--primary" type="submit">Simpan</button>
    </form>
    <section class="gk-card p-4 mt-4" aria-labelledby="portfolio-title">
      <div class="d-flex justify-content-between align-items-center flex-wrap gap-3 mb-3">
        <div><h2 id="portfolio-title" class="h5 fw-bold mb-1">Portofolio</h2><p class="small text-muted mb-0">Foto, video, atau PDF karyamu.</p></div>
        <button id="add-work" type="button" class="gk-btn gk-btn--primary" disabled aria-expanded="false" aria-controls="upload-form">Tambah karya</button>
      </div>
      <div id="portfolio-msg" aria-live="polite"></div>
      <form id="upload-form" class="border rounded p-3 mb-4" hidden>
        <div class="mb-3"><label for="work-file" class="form-label small fw-bold">Berkas</label>
          <input id="work-file" name="file" type="file" class="form-control" accept="${ACCEPT}" aria-describedby="work-file-help" required>
          <p id="work-file-help" class="small text-muted mt-1">JPG, PNG, WEBP, MP4, WEBM, atau PDF. Maksimal 50 MB; batas server dapat lebih kecil.</p>
          <div id="upload-preview" class="mt-2" hidden></div></div>
        <div class="mb-3"><label for="work-title" class="form-label small fw-bold">Judul</label><input id="work-title" name="title" class="form-control" required maxlength="150"></div>
        <div class="mb-3"><label for="work-description" class="form-label small fw-bold">Deskripsi (opsional)</label><textarea id="work-description" name="description" rows="2" class="form-control"></textarea></div>
        <div class="mb-3"><label for="work-visibility" class="form-label small fw-bold">Visibilitas</label><select id="work-visibility" name="visibility" class="form-select"><option value="PUBLIC">Publik</option><option value="PRIVATE">Privat</option></select></div>
        <div class="d-flex gap-2"><button type="submit" class="gk-btn gk-btn--primary">Unggah</button><button id="cancel-upload" type="button" class="gk-btn gk-btn--secondary">Batal</button></div>
      </form>
      <div id="work-grid" class="row row-cols-1 row-cols-sm-2 row-cols-lg-3 g-3" aria-live="polite"><p class="small text-muted">Memuat karya...</p></div>
    </section>
    <dialog id="work-dialog" class="gk-dialog" aria-labelledby="work-dialog-title"></dialog>`;

  const $ = (selector) => view.querySelector(selector);
  const grid = $("#work-grid");
  const upload = $("#upload-form");
  const dialog = $("#work-dialog");
  const controller = new AbortController();
  const urls = new Set();
  const thumbnailUrls = new Set();
  let items = [];
  let disposed = false;
  let uploadUrl = null;
  let dialogUrl = null;
  let dialogItem = null;
  let dialogRequest = 0;
  const track = (blob) => { const url = URL.createObjectURL(blob); urls.add(url); return url; };
  const release = (url) => { if (url) { URL.revokeObjectURL(url); urls.delete(url); } };
  const showError = (message) => { if (!disposed) $("#portfolio-msg").innerHTML = alertBox(message); };

  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) { observer.unobserve(entry.target); loadThumbnail(entry.target); }
    }
  }, { rootMargin: "200px" });

  window.addEventListener("hashchange", () => {
    disposed = true;
    controller.abort();
    observer.disconnect();
    dialog.close();
    for (const url of urls) URL.revokeObjectURL(url);
    urls.clear();
    thumbnailUrls.clear();
  }, { once: true });

  async function loadThumbnail(img) {
    try {
      const blob = await apiBlob(`/api/portfolios/${img.dataset.id}/content`, { signal: controller.signal });
      if (disposed || !img.isConnected) return;
      const url = track(blob);
      thumbnailUrls.add(url);
      img.src = url;
    } catch {
      if (!disposed && img.isConnected) img.replaceWith(document.createTextNode("Pratinjau tidak tersedia"));
    }
  }

  function renderGrid() {
    observer.disconnect();
    for (const url of thumbnailUrls) release(url);
    thumbnailUrls.clear();
    if ($("#work-count")) $("#work-count").textContent = items.length;
    grid.innerHTML = items.length ? items.map((p) => {
      const kind = kindOf(p.mimeType);
      const media = kind === "image" ? `<img data-id="${esc(p.id)}" alt="">`
        : `<i class="bi ${kind === "video" ? "bi-play-circle" : "bi-file-earmark-pdf"} fs-1" aria-hidden="true"></i>`;
      return `<div class="col"><button type="button" class="gk-card gk-card--interactive p-0 text-start w-100 h-100" data-work-id="${esc(p.id)}" aria-label="Buka ${esc(p.title)}">
        <span class="gk-card__thumb">${media}</span><span class="d-block p-3 text-break"><strong class="d-block">${esc(p.title)}</strong>
        <span class="small text-muted">${fmtSize(p.fileSize)} · ${p.visibility === "PRIVATE" ? "Privat" : "Publik"}</span></span></button></div>`;
    }).join("") : '<p class="text-muted mb-0">Belum ada karya.</p>';
    grid.querySelectorAll("img[data-id]").forEach((img) => observer.observe(img));
  }

  function resetUpload() {
    upload.reset();
    upload.hidden = true;
    $("#add-work").setAttribute("aria-expanded", "false");
    release(uploadUrl);
    uploadUrl = null;
    $("#upload-preview").replaceChildren();
    $("#upload-preview").hidden = true;
  }

  $("#add-work").onclick = () => {
    if (!upload.hidden) { resetUpload(); return; }
    upload.hidden = false;
    $("#add-work").setAttribute("aria-expanded", "true");
    upload.elements.file.focus();
  };
  $("#cancel-upload").onclick = () => { resetUpload(); $("#add-work").focus(); };
  upload.elements.file.onchange = () => {
    release(uploadUrl);
    uploadUrl = null;
    const preview = $("#upload-preview");
    preview.replaceChildren();
    preview.hidden = true;
    $("#portfolio-msg").replaceChildren();
    const file = upload.elements.file.files[0];
    if (!file) return;
    if (!file.size || file.size > MAX_BYTES || !ACCEPT.split(",").includes(file.type)) {
      upload.elements.file.value = "";
      showError(!file.size ? "File kosong." : file.size > MAX_BYTES ? "Ukuran file melebihi 50 MB." : "Pilih berkas JPG, PNG, WEBP, MP4, WEBM, atau PDF.");
      return;
    }
    if (!upload.elements.title.value) upload.elements.title.value = file.name.replace(/\.[^.]+$/, "").slice(0, 150);
    preview.hidden = false;
    const kind = kindOf(file.type);
    if (kind === "file") { preview.textContent = `${file.name} · ${fmtSize(file.size)}`; return; }
    uploadUrl = track(file);
    preview.innerHTML = `<div class="ratio ratio-16x9">${kind === "image"
      ? `<img class="object-fit-contain" src="${uploadUrl}" alt="Pratinjau karya">`
      : `<video class="object-fit-contain" src="${uploadUrl}" controls muted playsinline></video>`}</div>`;
  };

  upload.onsubmit = async (ev) => {
    ev.preventDefault();
    const button = upload.querySelector('[type="submit"]');
    const cancel = $("#cancel-upload");
    const add = $("#add-work");
    button.disabled = cancel.disabled = add.disabled = true;
    button.textContent = "Mengunggah...";
    $("#portfolio-msg").replaceChildren();
    try {
      const work = await api("/api/portfolios", { method: "POST", form: new FormData(upload) });
      if (disposed) return;
      items.unshift(work);
      renderGrid();
      resetUpload();
      add.focus();
      toast("Karya ditambahkan");
    } catch (e) { showError(e.message); }
    finally { button.disabled = cancel.disabled = add.disabled = false; button.textContent = "Unggah"; }
  };

  dialog.addEventListener("close", () => {
    dialogRequest++;
    dialogItem = null;
    release(dialogUrl);
    dialogUrl = null;
    dialog.replaceChildren();
  });

  async function openWork(work) {
    const request = ++dialogRequest;
    dialogItem = work;
    dialog.innerHTML = `<div class="gk-card p-4">
      <div class="d-flex justify-content-between align-items-start gap-3 mb-3"><h2 id="work-dialog-title" class="h5 fw-bold text-break">${esc(work.title)}</h2>
        <button type="button" class="gk-icon-btn" data-close aria-label="Tutup pratinjau"><i class="bi bi-x-lg" aria-hidden="true"></i></button></div>
      <div id="work-media" aria-live="polite">Memuat pratinjau...</div>
      ${work.description ? `<p class="mt-3 text-break">${esc(work.description)}</p>` : ""}
      <p class="small text-muted mt-3 text-break">${esc(work.fileName)} · ${fmtSize(work.fileSize)} · <span id="work-privacy">${work.visibility === "PRIVATE" ? "Privat" : "Publik"}</span></p>
      <div id="work-dialog-msg" aria-live="polite"></div>
      <div class="d-flex flex-wrap gap-2"><button type="button" class="gk-btn gk-btn--secondary" data-visibility>${work.visibility === "PRIVATE" ? "Jadikan publik" : "Jadikan privat"}</button>
        <button type="button" class="gk-btn gk-btn--danger" data-delete>Hapus karya</button></div></div>`;
    dialog.showModal();
    try {
      const blob = await apiBlob(`/api/portfolios/${work.id}/content`, { signal: controller.signal });
      if (disposed || request !== dialogRequest) return;
      dialogUrl = track(blob);
      const kind = kindOf(work.mimeType);
      dialog.querySelector("#work-media").innerHTML = kind === "file"
        ? `<a class="gk-btn gk-btn--primary" href="${dialogUrl}" target="_blank" rel="noopener">Buka PDF</a>`
        : `<div class="ratio ratio-16x9">${kind === "image"
          ? `<img class="object-fit-contain" src="${dialogUrl}" alt="${esc(work.title)}">`
          : `<video class="object-fit-contain" src="${dialogUrl}" controls playsinline></video>`}</div>`;
    } catch (e) {
      if (!disposed && request === dialogRequest) dialog.querySelector("#work-media").innerHTML = alertBox(e.message);
    }
  }

  grid.onclick = (ev) => {
    const button = ev.target.closest("button[data-work-id]");
    if (button) openWork(items.find((p) => String(p.id) === button.dataset.workId));
  };
  dialog.onclick = async (ev) => {
    if (ev.target.closest("[data-close]")) { dialog.close(); return; }
    const button = ev.target.closest("[data-visibility], [data-delete]");
    if (!button || !dialogItem) return;
    const work = dialogItem;
    const removing = button.hasAttribute("data-delete");
    if (removing && !confirm(`Hapus karya “${work.title}”?`)) return;
    const buttons = [...dialog.querySelectorAll("[data-visibility], [data-delete]")];
    buttons.forEach((b) => { b.disabled = true; });
    try {
      if (removing) {
        await api(`/api/portfolios/${work.id}`, { method: "DELETE" });
        if (disposed) return;
        items = items.filter((p) => p.id !== work.id);
        if (dialogItem === work) dialog.close();
        renderGrid();
        toast("Karya dihapus");
      } else {
        const visibility = work.visibility === "PRIVATE" ? "PUBLIC" : "PRIVATE";
        const updated = await api(`/api/portfolios/${work.id}`, { method: "PUT", body: { title: work.title, description: work.description, visibility } });
        if (disposed) return;
        Object.assign(work, updated);
        renderGrid();
        if (dialogItem === work) {
          dialog.querySelector("#work-privacy").textContent = work.visibility === "PRIVATE" ? "Privat" : "Publik";
          button.textContent = work.visibility === "PRIVATE" ? "Jadikan publik" : "Jadikan privat";
        }
        toast("Visibilitas diperbarui");
      }
    } catch (e) {
      if (!disposed && dialogItem === work) dialog.querySelector("#work-dialog-msg").innerHTML = alertBox(e.message);
    } finally { buttons.forEach((b) => { b.disabled = false; }); }
  };

  $("#f").onsubmit = async (ev) => {
    ev.preventDefault();
    const button = ev.target.querySelector('[type="submit"]');
    button.disabled = true;
    $("#msg").replaceChildren();
    try {
      const updated = await api("/api/me", { method: "PUT", body: Object.fromEntries(new FormData(ev.target)) });
      if (disposed) return;
      Object.assign(currentUser(), updated);
      const name = document.getElementById("gk-current-user");
      if (name) name.textContent = `${updated.fullName}${updated.role === "ADMIN" ? " (admin)" : ""}`;
      toast("Profil disimpan");
    } catch (e) { if (!disposed) $("#msg").innerHTML = alertBox(e.message); }
    finally { button.disabled = false; }
  };

  async function loadWorks() {
    $("#portfolio-msg").replaceChildren();
    grid.innerHTML = '<p class="small text-muted">Memuat karya...</p>';
    try {
      items = await api(`/api/portfolios/user/${me.id}`);
      if (disposed) return;
      renderGrid();
      $("#add-work").disabled = false;
    } catch (e) {
      if (disposed) return;
      showError(e.message);
      grid.innerHTML = '<div class="col"><button type="button" class="gk-btn gk-btn--secondary">Muat ulang karya</button></div>';
      grid.querySelector("button").onclick = loadWorks;
    }
  }
  await loadWorks();
}
