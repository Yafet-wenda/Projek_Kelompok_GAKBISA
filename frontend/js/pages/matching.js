import { api } from "../api.js";
import { currentQuery, navigate } from "../router.js";
import { esc, toast, alertBox } from "../ui.js";

export default async function page(view) {
  const params = currentQuery();
  const keyword = (params.get("keyword") || "").trim();
  const campus = params.get("campus") || "";
  const studyProgram = params.get("studyProgram") || "";
  const categoryId = params.get("categoryId") || "";
  const level = params.get("level") || "";
  const currentPage = Math.max(0, Number.parseInt(params.get("page") || "0", 10) || 0);
  const [candidatePage, categories, suggestions] = await Promise.all([
    api("/api/matching/search", { query: {
      keyword: keyword || undefined,
      campus: campus || undefined,
      studyProgram: studyProgram || undefined,
      categoryId: categoryId || undefined,
      level: level || undefined,
      page: currentPage,
      size: 20,
    } }),
    api("/api/categories").catch(() => []),
    keyword ? api("/api/skills", { query: { q: keyword, size: 8 } }).catch(() => ({ content: [] })) : { content: [] },
  ]);

  const shown = candidatePage.content || [];
  const total = candidatePage.totalElements || 0;
  const totalPages = candidatePage.totalPages || 0;

  const chips = (suggestions.content || []).length
    ? `<div class="mb-3 d-flex flex-wrap gap-2 align-items-center">
         <span class="small text-muted">Keahlian di katalog:</span>
         ${suggestions.content.map((s) => `
           <a class="gk-badge text-decoration-none" href="#/matching?keyword=${encodeURIComponent(s.name)}"
              title="${esc(s.category)}">${esc(s.name)}</a>`).join("")}
       </div>`
    : "";

  const card = (c) => `
    <div class="gk-card p-3 mb-2">
      <div class="d-flex justify-content-between align-items-start gap-3 flex-wrap">
        <div class="min-w-0">
          <div><a href="#/profile/${esc(c.userId)}" class="fw-bold text-decoration-none" style="color:var(--gk-ink)" title="Lihat profil publik">${esc(c.fullName)}</a></div>
          <div class="small text-muted mb-1">${esc([c.studyProgram, c.campus].filter(Boolean).join(" · ") || "Mahasiswa GAKBISA")}</div>
          <ul class="small mb-0 ps-3">${(c.reasons || []).map((r) => `<li>${esc(r)}</li>`).join("")}</ul>
        </div>
        <div class="text-end">
          <span class="gk-badge">${esc(c.score)} poin</span>
          <div class="mt-2">
            <button class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)"
                    data-barter="${esc(c.userId)}" data-name="${esc(c.fullName)}">
              <i class="bi bi-arrow-left-right me-1"></i>Ajukan barter
            </button>
          </div>
        </div>
      </div>
    </div>`;

  const hasFilters = Boolean(keyword || campus || studyProgram || categoryId || level);
  const empty = hasFilters
    ? "Tidak ada partner yang cocok dengan filter ini. Coba ubah atau kurangi filternya."
    : "Belum ada rekomendasi. Lengkapi daftar keahlian yang kamu tawarkan (OFFER) dan ingin dipelajari (WANT) di profilmu.";

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3"><i class="bi bi-search me-2"></i>Cari partner</h1>
    <form id="gk-search-form" class="row g-2 mb-3" role="search">
      <div class="col-md-6 col-lg-4">
      <input id="gk-search-input" name="keyword" type="search" class="form-control"
             placeholder="Cari nama, kampus, program studi, atau keahlian…"
             value="${esc(keyword)}" autocomplete="off">
      </div>
      <div class="col-md-6 col-lg-2"><input class="form-control" name="campus" placeholder="Kampus" value="${esc(campus)}"></div>
      <div class="col-md-6 col-lg-2"><input class="form-control" name="studyProgram" placeholder="Program studi" value="${esc(studyProgram)}"></div>
      <div class="col-md-6 col-lg-2"><select class="form-select" name="categoryId"><option value="">Semua kategori</option>${categories.map((c) => `<option value="${esc(c.id)}" ${categoryId === c.id ? "selected" : ""}>${esc(c.name)}</option>`).join("")}</select></div>
      <div class="col-md-6 col-lg-1"><select class="form-select" name="level"><option value="">Semua level</option>${[["BEGINNER","Pemula"],["INTERMEDIATE","Menengah"],["ADVANCED","Mahir"]].map(([v,l]) => `<option value="${v}" ${level === v ? "selected" : ""}>${l}</option>`).join("")}</select></div>
      <div class="col-md-6 col-lg-1"><button class="gk-btn text-white w-100" style="background:var(--gk-primary)">Cari</button></div>
    </form>
    ${chips}
    <div id="msg"></div>
    <p class="small text-muted">${total} partner ditemukan${total ? ` · halaman ${currentPage + 1} dari ${totalPages}` : ""}</p>
    ${shown.length ? shown.map(card).join("") : `<div class="gk-card p-3 text-muted">${empty}</div>`}
    ${totalPages > 1 ? `<nav class="d-flex justify-content-between align-items-center mt-3" aria-label="Halaman partner">
      <button class="gk-btn gk-btn--secondary" data-page="${currentPage - 1}" ${currentPage <= 0 ? "disabled" : ""}>Sebelumnya</button>
      <span class="small text-muted">${currentPage + 1} / ${totalPages}</span>
      <button class="gk-btn gk-btn--secondary" data-page="${currentPage + 1}" ${currentPage + 1 >= totalPages ? "disabled" : ""}>Berikutnya</button>
    </nav>` : ""}
    <dialog id="gk-barter-dialog" class="gk-dialog"></dialog>`;

  view.querySelector("#gk-search-form").onsubmit = (ev) => {
    ev.preventDefault();
    const d = new FormData(ev.target); const params = new URLSearchParams();
    const v = d.get("keyword").toString().trim(); if (v) params.set("keyword", v);
    if (d.get("campus").toString().trim()) params.set("campus", d.get("campus").toString().trim());
    if (d.get("studyProgram").toString().trim()) params.set("studyProgram", d.get("studyProgram").toString().trim());
    if (d.get("categoryId")) params.set("categoryId", d.get("categoryId"));
    if (d.get("level")) params.set("level", d.get("level"));
    navigate("/matching" + (params.size ? "?" + params.toString() : ""));
  };

  view.onclick = (ev) => {
    const pageButton = ev.target.closest("button[data-page]");
    if (pageButton && !pageButton.disabled) {
      const next = new URLSearchParams(params);
      next.set("page", pageButton.dataset.page);
      navigate("/matching?" + next.toString());
      return;
    }
    const btn = ev.target.closest("button[data-barter]");
    if (btn) openBarterDialog(view, btn.dataset.barter, btn.dataset.name);
  };
}

async function openBarterDialog(view, targetUserId, name) {
  const dlg = view.querySelector("#gk-barter-dialog");
  let offers, wants;
  try {
    [offers, wants] = await Promise.all([
      api("/api/me/skills", { query: { type: "OFFER" } }),
      api("/api/me/skills", { query: { type: "WANT" } }),
    ]);
  } catch (e) {
    view.querySelector("#msg").innerHTML = alertBox(e.message);
    return;
  }

  const opt = (s) => `<option value="${esc(s.id)}">${esc(s.skillName)}${s.level ? ` (${esc(s.level)})` : ""}</option>`;
  const missing = !offers.length || !wants.length;

  dlg.innerHTML = `
    <form method="dialog" id="gk-barter-form" class="gk-card p-4 d-flex flex-column gap-3">
      <div class="d-flex justify-content-between align-items-start">
        <h2 class="h5 fw-bold m-0">Ajukan barter ke ${esc(name)}</h2>
        <button type="button" class="gk-icon-btn" data-close aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
      </div>
      <div id="dlg-msg"></div>
      ${missing ? alertBox("Kamu perlu punya minimal satu keahlian OFFER dan satu WANT di profilmu sebelum mengajukan barter.", "warning") : ""}
      <div><label class="form-label small fw-bold">Keahlian yang kamu tawarkan</label>
        <select name="offered" class="form-select" required>
          <option value="">— pilih keahlian yang kamu ajarkan —</option>${offers.map(opt).join("")}</select></div>
      <div><label class="form-label small fw-bold">Keahlian yang ingin kamu pelajari</label>
        <select name="wanted" class="form-select" required>
          <option value="">— pilih keahlian yang ingin dipelajari —</option>${wants.map(opt).join("")}</select></div>
      <div><label class="form-label small fw-bold">Pesan (opsional)</label>
        <textarea name="message" class="form-control" rows="3" maxlength="2000"></textarea></div>
      <div class="d-flex justify-content-end gap-2">
        <button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button>
        <button class="gk-btn text-white" style="background:var(--gk-primary)" ${missing ? "disabled" : ""}>Kirim permintaan</button>
      </div>
    </form>`;

  dlg.querySelectorAll("[data-close]").forEach((b) => (b.onclick = () => dlg.close()));
  dlg.querySelector("#gk-barter-form").onsubmit = async (ev) => {
    ev.preventDefault();
    const d = new FormData(ev.target);
    try {
      await api("/api/barter", {
        method: "POST",
        body: {
          targetUserId,
          offeredUserSkillId: d.get("offered"),
          wantedUserSkillId: d.get("wanted"),
          message: d.get("message") || null,
        },
      });
      dlg.close();
      toast("Permintaan barter terkirim");
      navigate("/barter");
    } catch (e) {
      dlg.querySelector("#dlg-msg").innerHTML = alertBox(e.message);
    }
  };
  dlg.showModal();
}
