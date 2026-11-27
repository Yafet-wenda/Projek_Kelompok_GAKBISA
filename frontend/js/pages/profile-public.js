import { api, apiBlob } from "../api.js";
import { currentUser } from "../auth.js";
import { navigate } from "../router.js";
import { esc, fmtDate, toast, alertBox } from "../ui.js";

const kindOf = (m = "") => (m.startsWith("image/") ? "image" : m.startsWith("video/") ? "video" : "file");
const KIND = { image: "Foto", video: "Video", file: "PDF" };
const initials = (n = "") => n.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join("") || "?";
const fmtSize = (b) => (b >= 1048576 ? (b / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(b / 1024)) + " KB");

export default async function page(view, { id }) {
  const me = currentUser();
  const isMe = me && me.id === id;

  const [u, works, revs] = await Promise.all([
    api(`/api/users/${id}`).catch(() => null),
    api(`/api/portfolios/user/${id}`).catch(() => []),
    api(`/api/reviews/user/${id}`).catch(() => []),
  ]);

  if (!u) {
    view.innerHTML = `
      <div class="container py-4">
        ${alertBox("Profil pengguna tidak ditemukan atau akun dinonaktifkan.", "warning")}
        <a href="#/matching" class="btn btn-sm btn-outline-secondary mt-2">&larr; Kembali ke pencarian partner</a>
      </div>`;
    return;
  }

  const urls = new Set();
  const track = (blob) => { const url = URL.createObjectURL(blob); urls.add(url); return url; };
  window.addEventListener("hashchange", () => urls.forEach((u) => URL.revokeObjectURL(u)), { once: true });

  const offers = u.offers || [];

  view.innerHTML = `
    <div class="mb-3 d-flex justify-content-between align-items-center">
      <a href="#/matching" class="small text-decoration-none">&larr; Cari partner</a>
      ${!isMe ? `
        <button class="btn btn-sm btn-outline-danger" id="btn-report-user">
          <i class="bi bi-flag me-1"></i>Laporkan pengguna
        </button>` : ""}
    </div>
    <div id="pub-msg"></div>

    <!-- Hero Profil Publik -->
    <section class="gp-glass gp-hero">
      <div class="gp-avatar" aria-hidden="true">${u.avatarPath ? `<img src="${esc(u.avatarPath)}" alt="" class="w-100 h-100 rounded-circle object-fit-cover">` : esc(initials(u.fullName))}</div>
      <div class="gp-id">
        <h1 id="gp-name">${esc(u.fullName)} ${u.verified ? '<span class="badge bg-success align-middle">Terverifikasi</span>' : ""}</h1>
        <p id="gp-sub">${esc([u.campus, u.studyProgram].filter(Boolean).join(" · ") || "Mahasiswa GAKBISA")}</p>
        ${u.interests ? `<p class="small text-muted">Minat: ${esc(u.interests)}</p>` : ""}
        <div class="d-flex flex-wrap gap-2 mt-2">
          ${isMe ? `
            <a href="#/profile" class="gk-btn gk-btn--sm gk-btn--secondary">
              <i class="bi bi-pencil me-1"></i>Edit profil saya
            </a>` : `
            <button id="btn-start-barter" class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)">
              <i class="bi bi-arrow-left-right me-1"></i>Ajukan barter keahlian
            </button>`}
        </div>
      </div>
      <dl class="gp-stats">
        <div><dt>Rating</dt><dd>${u.averageRating == null ? "-" : Number(u.averageRating).toFixed(1)} ★</dd></div>
        <div><dt>Ulasan</dt><dd>${esc(u.reviewCount ?? revs.length)}</dd></div>
        <div><dt>Karya</dt><dd>${works.length}</dd></div>
        <div><dt>Sesi selesai</dt><dd>${esc(u.completedSessionCount ?? 0)}</dd></div>
      </dl>
    </section>

    <!-- Keahlian yang Diajarkan -->
    <section class="gk-card p-4 mb-4">
      <h2 class="h6 fw-bold mb-3"><i class="bi bi-award text-primary me-2"></i>Keahlian yang Ditawarkan (Diajarkan)</h2>
      ${offers.length ? `
        <div class="d-flex flex-wrap gap-2">
          ${offers.map((s) => `<span class="badge bg-primary rounded-pill px-3 py-2" style="font-size:13px">${esc(s)}</span>`).join("")}
        </div>` : `
        <p class="small text-muted mb-0">Pengguna ini belum menambahkan keahlian yang diajarkan.</p>`}
    </section>

    <div class="row g-4">
      <!-- Portofolio -->
      <div class="col-lg-7">
        <section class="gk-card p-4 h-100">
          <div class="d-flex justify-content-between align-items-center mb-3">
            <h2 class="h6 fw-bold m-0"><i class="bi bi-images text-info me-2"></i>Portofolio &amp; Karya (${works.length})</h2>
          </div>
          <div id="pub-grid" class="gp-grid"></div>
        </section>
      </div>

      <!-- Ulasan Pengguna -->
      <div class="col-lg-5">
        <section class="gk-card p-4 h-100">
          <h2 class="h6 fw-bold mb-3"><i class="bi bi-star-fill text-warning me-2"></i>Ulasan Partner (${revs.length})</h2>
          <div class="d-flex flex-column gap-3" style="max-height: 480px; overflow-y: auto;">
            ${revs.length ? revs.map((r) => `
              <div class="p-3 rounded-3" style="background:rgba(127,140,170,0.08);border:1px solid var(--gk-border)">
                <div class="d-flex justify-content-between align-items-center mb-1">
                  <span class="text-warning fw-bold">${"★".repeat(r.rating || 5)}${"☆".repeat(5 - (r.rating || 5))}</span>
                  <span class="small text-muted">${fmtDate(r.createdAt)}</span>
                </div>
                <p class="small mb-0" style="color:var(--gk-ink)">${esc(r.comment || "(Tidak ada catatan ulasan)")}</p>
              </div>`).join("") : `
              <p class="small text-muted mb-0">Belum ada ulasan dari partner belajar.</p>`}
          </div>
        </section>
      </div>
    </div>

    <!-- Dialog Barter -->
    <dialog id="pub-barter-dlg" class="gk-dialog"></dialog>

    <!-- Dialog Lapor -->
    <dialog id="pub-report-dlg" class="gk-dialog"></dialog>

    <!-- Lightbox Portofolio -->
    <div id="pub-lb" class="gp-lb" hidden></div>`;

  const $ = (s) => view.querySelector(s);

  // ---------- Grid Karya Portofolio ----------
  const grid = $("#pub-grid");
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
      <span class="gp-cap"><b>${esc(p.title)}</b><small>${KIND[k]} · ${fmtSize(p.fileSize)}</small></span></button>`;
  };

  grid.innerHTML = works.length ? works.map(tile).join("")
    : `<div class="gp-empty"><i class="bi bi-images"></i><b>Belum ada karya</b><p>Pengguna ini belum membagikan karya publik.</p></div>`;
  grid.querySelectorAll("img[data-id]").forEach((img) => (thumbIo ? thumbIo.observe(img) : loadThumb(img)));

  // ---------- Lightbox Portofolio ----------
  const lb = $("#pub-lb");
  let lbUrl = null;
  const onKey = (e) => e.key === "Escape" && closeLb();
  function closeLb() {
    lb.hidden = true; lb.innerHTML = "";
    if (lbUrl) { URL.revokeObjectURL(lbUrl); urls.delete(lbUrl); lbUrl = null; }
    document.removeEventListener("keydown", onKey);
  }
  async function openLb(p) {
    lb.hidden = false;
    lb.innerHTML = `<div class="gp-lb__box gp-glass" role="dialog" aria-modal="true" aria-label="${esc(p.title)}">
      <button class="gp-lb__x" data-act="close" aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
      <div class="gp-lb__media"><span class="gp-spin"></span></div>
      <div class="gp-lb__info">
        <h3>${esc(p.title)}</h3>
        ${p.description ? `<p>${esc(p.description)}</p>` : ""}
        <small>${esc(p.fileName)} · ${fmtSize(p.fileSize)}</small>
        <div class="mt-2">
          <button class="btn btn-sm btn-outline-danger" data-report-portfolio="${esc(p.id)}">
            <i class="bi bi-flag me-1"></i>Laporkan karya ini
          </button>
        </div>
      </div>
    </div>`;
    document.addEventListener("keydown", onKey);
    lb.querySelector("[data-act=close]").focus();
    const media = lb.querySelector(".gp-lb__media");
    try {
      const blob = await apiBlob(`/api/portfolios/${p.id}/content`);
      lbUrl = track(blob);
      const k = kindOf(p.mimeType);
      media.innerHTML = k === "image" ? `<img src="${lbUrl}" alt="${esc(p.title)}">`
        : k === "video" ? `<video src="${lbUrl}" controls autoplay playsinline></video>`
        : `<div class="gp-pdf"><i class="bi bi-file-earmark-pdf"></i><a class="gp-btn gp-btn--pri" href="${lbUrl}" target="_blank" rel="noopener">Buka PDF</a></div>`;
    } catch (e) { media.innerHTML = `<p class="gp-err">${esc(e.message)}</p>`; }
  }

  grid.onclick = (ev) => {
    const t = ev.target.closest(".gp-tile");
    if (t) openLb(works.find((x) => String(x.id) === t.dataset.id));
  };
  lb.onclick = (ev) => {
    if (ev.target === lb || ev.target.closest("[data-act=close]")) return closeLb();
    const repBtn = ev.target.closest("button[data-report-portfolio]");
    if (repBtn) {
      closeLb();
      openReportDialog("PORTFOLIO", repBtn.dataset.reportPortfolio, p?.title || "Karya Portofolio");
    }
  };

  // ---------- Dialog Barter ----------
  const barterBtn = $("#btn-start-barter");
  if (barterBtn) {
    barterBtn.onclick = async () => {
      const dlg = $("#pub-barter-dlg");
      let offersList, wantsList;
      try {
        [offersList, wantsList] = await Promise.all([
          api("/api/me/skills", { query: { type: "OFFER" } }),
          api("/api/me/skills", { query: { type: "WANT" } }),
        ]);
      } catch (e) {
        $("#pub-msg").innerHTML = alertBox(e.message);
        return;
      }
      const opt = (s) => `<option value="${esc(s.id)}">${esc(s.skillName)}${s.level ? ` (${esc(s.level)})` : ""}</option>`;
      const missing = !offersList.length || !wantsList.length;

      dlg.innerHTML = `
        <form method="dialog" id="pub-barter-form" class="gk-card p-4 d-flex flex-column gap-3">
          <div class="d-flex justify-content-between align-items-start">
            <h2 class="h5 fw-bold m-0">Ajukan barter ke ${esc(u.fullName)}</h2>
            <button type="button" class="gk-icon-btn" data-close aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
          </div>
          <div id="dlg-msg"></div>
          ${missing ? alertBox("Kamu perlu punya minimal satu keahlian OFFER dan satu WANT di profilmu sebelum mengajukan barter.", "warning") : ""}
          <div><label class="form-label small fw-bold">Keahlian yang kamu ajarkan</label>
            <select name="offered" class="form-select" required>
              <option value="">— pilih keahlian yang kamu tawarkan —</option>${offersList.map(opt).join("")}</select></div>
          <div><label class="form-label small fw-bold">Keahlian yang ingin kamu pelajari</label>
            <select name="wanted" class="form-select" required>
              <option value="">— pilih keahlian yang ingin dipelajari —</option>${wantsList.map(opt).join("")}</select></div>
          <div><label class="form-label small fw-bold">Pesan pengantar (opsional)</label>
            <textarea name="message" class="form-control" rows="3" maxlength="2000" placeholder="Ceritakan tujuan bartermu…"></textarea></div>
          <div class="d-flex justify-content-end gap-2">
            <button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button>
            <button class="gk-btn text-white" style="background:var(--gk-primary)" ${missing ? "disabled" : ""}>Kirim permintaan barter</button>
          </div>
        </form>`;

      dlg.querySelectorAll("[data-close]").forEach((b) => (b.onclick = () => dlg.close()));
      dlg.querySelector("#pub-barter-form").onsubmit = async (ev) => {
        ev.preventDefault();
        const d = new FormData(ev.target);
        try {
          await api("/api/barter", {
            method: "POST",
            body: {
              targetUserId: id,
              offeredUserSkillId: d.get("offered"),
              wantedUserSkillId: d.get("wanted"),
              message: d.get("message") || null,
            },
          });
          dlg.close();
          toast("Permintaan barter berhasil dikirim");
          navigate("/barter");
        } catch (e) {
          dlg.querySelector("#dlg-msg").innerHTML = alertBox(e.message);
        }
      };
      dlg.showModal();
    };
  }

  // ---------- Dialog Laporkan Pengguna / Konten ----------
  function openReportDialog(targetType, targetId, targetLabel) {
    const dlg = $("#pub-report-dlg");
    dlg.innerHTML = `
      <form method="dialog" id="form-report" class="gk-card p-4 d-flex flex-column gap-3">
        <div class="d-flex justify-content-between align-items-start">
          <h2 class="h5 fw-bold m-0 text-danger"><i class="bi bi-shield-exclamation me-1"></i>Laporkan ${esc(targetLabel)}</h2>
          <button type="button" class="gk-icon-btn" data-close aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
        </div>
        <div id="rep-dlg-msg"></div>
        <p class="small text-muted mb-0">Laporan akan diteruskan ke tim moderator admin GAKBISA untuk ditinjau.</p>
        <div>
          <label class="form-label small fw-bold">Alasan pelaporan</label>
          <textarea name="reason" class="form-control" rows="3" required maxlength="1000"
                    placeholder="Jelaskan pelanggaran (mis. spam, konten tidak pantas, penipuan)…"></textarea>
        </div>
        <div class="d-flex justify-content-end gap-2">
          <button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button>
          <button class="gk-btn gk-btn--danger">Kirim Laporan</button>
        </div>
      </form>`;

    dlg.querySelectorAll("[data-close]").forEach((b) => (b.onclick = () => dlg.close()));
    dlg.querySelector("#form-report").onsubmit = async (ev) => {
      ev.preventDefault();
      const reason = new FormData(ev.target).get("reason").toString().trim();
      try {
        await api("/api/reports", {
          method: "POST",
          body: { targetType, targetId, reason },
        });
        dlg.close();
        toast("Laporan telah dikirim ke moderator");
      } catch (e) {
        dlg.querySelector("#rep-dlg-msg").innerHTML = alertBox(e.message);
      }
    };
    dlg.showModal();
  }

  const repUserBtn = $("#btn-report-user");
  if (repUserBtn) {
    repUserBtn.onclick = () => openReportDialog("USER", id, u.fullName);
  }
}
