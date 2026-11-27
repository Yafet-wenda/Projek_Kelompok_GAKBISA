import { api } from "../api.js";
import { currentUser } from "../auth.js";
import { currentQuery, navigate } from "../router.js";
import { esc, toast, alertBox } from "../ui.js";

export default async function page(view) {
  const q = (currentQuery().get("q") || "").trim();
  const categoryId = currentQuery().get("categoryId") || "";
  const pageNum = parseInt(currentQuery().get("page") || "0", 10);
  const pageSize = 12;
  let catalogError = "";

  const [categories, skillPage] = await Promise.all([
    api("/api/categories").catch(() => []),
    api("/api/skills", {
      query: {
        page: pageNum,
        size: pageSize,
        q: q || undefined,
        categoryId: categoryId || undefined,
      },
    }).catch((error) => {
      catalogError = error.message || "Terjadi kesalahan saat memuat katalog keahlian.";
      return { content: [], totalPages: 0, totalElements: 0, number: 0 };
    }),
  ]);

  const skills = skillPage.content || [];
  const totalPages = skillPage.totalPages || 0;
  const totalElements = skillPage.totalElements || 0;

  const catButtons = `
    <div class="d-flex flex-wrap gap-2 mb-3">
      <a href="#/catalog${q ? `?q=${encodeURIComponent(q)}` : ""}"
         class="btn btn-sm ${!categoryId ? "btn-primary" : "btn-outline-secondary"} rounded-pill">
        Semua Kategori
      </a>
      ${categories.map((c) => {
        const active = c.id === categoryId;
        const queryParams = new URLSearchParams();
        if (q) queryParams.set("q", q);
        queryParams.set("categoryId", c.id);
        return `
          <a href="#/catalog?${queryParams.toString()}"
             class="btn btn-sm ${active ? "btn-primary" : "btn-outline-secondary"} rounded-pill">
            ${esc(c.name)}
          </a>`;
      }).join("")}
    </div>`;

  const card = (s) => `
    <div class="col-md-6 col-lg-4">
      <div class="gk-card p-3 h-100 d-flex flex-column justify-content-between">
        <div>
          <div class="d-flex justify-content-between align-items-start gap-2 mb-2">
            <h2 class="h6 fw-bold m-0" style="color:var(--gk-ink)">${esc(s.name)}</h2>
            <span class="gk-badge" style="font-size:11px">${esc(s.category)}</span>
          </div>
          <p class="small text-muted mb-3" style="min-height:36px">
            ${esc(s.description || "Tidak ada deskripsi khusus untuk keahlian ini.")}
          </p>
        </div>
        <div class="d-flex flex-wrap gap-2 pt-2 border-top">
          <a href="#/matching?keyword=${encodeURIComponent(s.name)}"
             class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary);font-size:12px">
            <i class="bi bi-search me-1"></i>Cari pengajar
          </a>
          ${currentUser() ? `
          <div class="dropdown">
            <button class="gk-btn gk-btn--sm dropdown-toggle" type="button" data-bs-toggle="dropdown"
                    style="font-size:12px;background:rgba(127,140,170,0.12)">
              + Tambah ke Profil
            </button>
            <ul class="dropdown-menu">
              <li><button class="dropdown-item small" data-add-skill="${esc(s.id)}" data-skill-name="${esc(s.name)}" data-type="OFFER">
                <i class="bi bi-award me-2 text-primary"></i>Sebagai Keahlian yang Diajarkan (OFFER)
              </button></li>
              <li><button class="dropdown-item small" data-add-skill="${esc(s.id)}" data-skill-name="${esc(s.name)}" data-type="WANT">
                <i class="bi bi-lightbulb me-2 text-warning"></i>Sebagai Keahlian yang Dipelajari (WANT)
              </button></li>
            </ul>
          </div>` : ""}
        </div>
      </div>
    </div>`;

  const pagination = totalPages > 1 ? `
    <nav class="d-flex justify-content-center align-items-center gap-2 mt-4" aria-label="Navigasi halaman">
      <button class="btn btn-sm btn-outline-secondary" id="page-prev" ${pageNum <= 0 ? "disabled" : ""}>
        &larr; Sebelumnya
      </button>
      <span class="small text-muted">Halaman ${pageNum + 1} dari ${totalPages}</span>
      <button class="btn btn-sm btn-outline-secondary" id="page-next" ${pageNum >= totalPages - 1 ? "disabled" : ""}>
        Berikutnya &rarr;
      </button>
    </nav>` : "";

  view.innerHTML = `
    <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-3">
      <div>
        <h1 class="h4 fw-bold m-0"><i class="bi bi-grid me-2"></i>Katalog Keahlian</h1>
        <p class="small text-muted mb-0">Jelajahi berbagai keahlian dan temukan mahasiswa yang siap saling berbagi ilmu.</p>
      </div>
      <span class="badge bg-secondary rounded-pill px-3 py-2">${catalogError ? "Katalog tidak tersedia" : `${totalElements} total keahlian`}</span>
    </div>

    <form id="catalog-search-form" class="d-flex gap-2 mb-3" role="search">
      <input name="q" type="search" class="form-control"
             placeholder="Cari keahlian (mis. React, Desain Grafis, Statistika, Bahasa Jepang)…"
             value="${esc(q)}" autocomplete="off">
      <button class="gk-btn text-white" style="background:var(--gk-primary)">Cari</button>
      ${q || categoryId ? `<a href="#/catalog" class="gk-btn gk-btn--secondary">Reset</a>` : ""}
    </form>

    ${catButtons}
    <div id="catalog-msg"></div>

    <div class="row g-3">
      ${skills.length ? skills.map(card).join("") : `
        <div class="col-12">
          <div class="gk-card p-4 text-center text-muted">
            <i class="bi ${catalogError ? "bi-exclamation-circle" : "bi-search"} fs-2 mb-2"></i>
            <p class="mb-0">${catalogError ? "Katalog belum dapat ditampilkan. Coba muat ulang beberapa saat lagi." : `Tidak ada keahlian yang ditemukan${q ? ` untuk “${esc(q)}”` : ""}.`}</p>
          </div>
        </div>`}
    </div>

    ${pagination}`;

  if (catalogError) {
    view.querySelector("#catalog-msg").innerHTML = alertBox(`Gagal memuat katalog keahlian: ${catalogError}`);
  }

  // Form search handler
  view.querySelector("#catalog-search-form").onsubmit = (ev) => {
    ev.preventDefault();
    const keyword = new FormData(ev.target).get("q").toString().trim();
    const params = new URLSearchParams();
    if (keyword) params.set("q", keyword);
    if (categoryId) params.set("categoryId", categoryId);
    navigate("/catalog" + (params.toString() ? "?" + params.toString() : ""));
  };

  // Pagination buttons
  const prevBtn = view.querySelector("#page-prev");
  const nextBtn = view.querySelector("#page-next");
  if (prevBtn) {
    prevBtn.onclick = () => {
      const params = new URLSearchParams();
      if (q) params.set("q", q);
      if (categoryId) params.set("categoryId", categoryId);
      params.set("page", String(pageNum - 1));
      navigate("/catalog?" + params.toString());
    };
  }
  if (nextBtn) {
    nextBtn.onclick = () => {
      const params = new URLSearchParams();
      if (q) params.set("q", q);
      if (categoryId) params.set("categoryId", categoryId);
      params.set("page", String(pageNum + 1));
      navigate("/catalog?" + params.toString());
    };
  }

  // Quick add skill button
  view.onclick = async (ev) => {
    const btn = ev.target.closest("button[data-add-skill]");
    if (!btn) return;
    const skillId = btn.dataset.addSkill;
    const skillName = btn.dataset.skillName;
    const type = btn.dataset.type;
    try {
      await api("/api/me/skills", {
        method: "POST",
        body: { skillId, skillName, type, level: "INTERMEDIATE" },
      });
      toast(`Keahlian “${skillName}” ditambahkan ke ${type === "OFFER" ? "OFFER" : "WANT"}`);
    } catch (e) {
      view.querySelector("#catalog-msg").innerHTML = alertBox(e.message);
    }
  };
}
