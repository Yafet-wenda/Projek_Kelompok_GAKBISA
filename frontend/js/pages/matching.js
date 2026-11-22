import { api } from "../api.js";
import { currentQuery, navigate } from "../router.js";
import { esc, toast, alertBox } from "../ui.js";

/**
 * Pencarian partner. Daftar kandidat berskor diambil dari /api/matching/candidates,
 * lalu disaring di browser berdasarkan kata kunci (nama, kampus, atau alasan kecocokan,
 * sama seperti halaman /matching versi lama). Saran keahlian diambil dari katalog publik.
 */
export default async function page(view) {
  const keyword = (currentQuery().get("keyword") || "").trim();
  const kw = keyword.toLowerCase();

  const [candidates, suggestions] = await Promise.all([
    api("/api/matching/candidates", { query: { limit: 50 } }),
    keyword ? api("/api/skills", { query: { q: keyword, size: 8 } }).catch(() => ({ content: [] })) : { content: [] },
  ]);

  const shown = candidates.filter((c) =>
    !kw ||
    (c.fullName || "").toLowerCase().includes(kw) ||
    (c.campus || "").toLowerCase().includes(kw) ||
    (c.reasons || []).some((r) => r.toLowerCase().includes(kw)));

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
          <div class="fw-bold">${esc(c.fullName)}</div>
          <div class="small text-muted mb-1">${esc(c.campus || "Mahasiswa GAKBISA")}</div>
          <ul class="small mb-0 ps-3">${(c.reasons || []).map((r) => `<li>${esc(r)}</li>`).join("")}</ul>
        </div>
        <div class="text-end">
          <span class="gk-badge">${esc(c.score)} poin</span>
          <div class="mt-2">
            <button class="gk-btn gk-btn--primary gk-btn--sm"
                    data-barter="${esc(c.userId)}" data-name="${esc(c.fullName)}">
              <i class="bi bi-arrow-left-right me-1"></i>Ajukan barter
            </button>
          </div>
        </div>
      </div>
    </div>`;

  const empty = keyword
    ? `Tidak ada partner yang cocok dengan “${esc(keyword)}”. Coba kata kunci lain, atau kosongkan pencarian untuk melihat semua rekomendasi.`
    : "Belum ada rekomendasi. Lengkapi daftar keahlian yang kamu tawarkan (OFFER) dan ingin dipelajari (WANT) di profilmu.";

  view.innerHTML = `
    <h1 class="h4 fw-bold mb-3"><i class="bi bi-search me-2"></i>Cari partner</h1>
    <form id="gk-search-form" class="d-flex gap-2 mb-3" role="search">
      <input id="gk-search-input" name="keyword" type="search" class="form-control"
             placeholder="Cari nama, kampus, atau keahlian (mis. Python, Desain)…"
             value="${esc(keyword)}" autocomplete="off">
      <button class="gk-btn gk-btn--primary">Cari</button>
    </form>
    ${chips}
    <div id="msg"></div>
    <p class="small text-muted">${keyword ? `${shown.length} hasil untuk “${esc(keyword)}”` : `${shown.length} rekomendasi partner`}</p>
    ${shown.length ? shown.map(card).join("") : `<div class="gk-card p-3 text-muted">${empty}</div>`}
    <dialog id="gk-barter-dialog" class="gk-dialog"></dialog>`;

  view.querySelector("#gk-search-form").onsubmit = (ev) => {
    ev.preventDefault();
    const v = new FormData(ev.target).get("keyword").toString().trim();
    navigate("/matching" + (v ? "?keyword=" + encodeURIComponent(v) : ""));
  };

  view.onclick = (ev) => {
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
        <button class="gk-btn gk-btn--primary" ${missing ? "disabled" : ""}>Kirim permintaan</button>
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