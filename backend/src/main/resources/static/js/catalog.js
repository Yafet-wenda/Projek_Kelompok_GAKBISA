/* Katalog publik: baca /api/skills, /api/categories, /api/portfolios tanpa login. */
(function () {
  "use strict";
  const { api, html, toast, ukuran, tanggal } = window.Gakbisa;

  const state = {
    q: "",
    kategori: new URLSearchParams(location.search).get("kategori") || "",
    halaman: 0,
    ukuran: 12,
    totalHalaman: 1,
    loading: false,
    login: false,
  };
  const profilCache = new Map();

  const gridSkill = document.getElementById("grid-keahlian");
  const chipBox = document.getElementById("kategori-chips");
  const cari = document.getElementById("cari");
  const CHIP = "btn btn-sm rounded-pill btn-outline-primary";

  /* ---------- kategori ---------- */
  async function muatKategori() {
    try {
      const daftar = await api.get("/api/categories");
      chipBox.innerHTML = html`
        <button class="${CHIP} ${state.kategori ? "" : "active"}" type="button" data-id="">Semua kategori</button>
        ${daftar.map((c) => html`
          <button class="${CHIP} ${state.kategori === c.id ? "active" : ""}" type="button"
                  data-id="${c.id}" title="${c.description || ""}">${c.name}</button>`)}
      `;
    } catch (e) {
      chipBox.innerHTML = html`<span class="text-muted small">Kategori gagal dimuat (${e.message}).</span>`;
    }
  }

  /* ---------- keahlian ---------- */
  function kartuSkill(s) {
    return html`
      <div class="list-group-item">
        <div class="d-flex justify-content-between align-items-start gap-3">
          <div class="min-w-0">
            <h3 class="h6 mb-1">${s.name}</h3>
            <p class="text-muted small mb-0">${s.description || "Belum ada keterangan."}</p>
          </div>
          <span class="gk-badge gk-badge--neutral text-nowrap">${s.category || "umum"}</span>
        </div>
      </div>`;
  }

  async function muatKeahlian(append) {
    if (state.loading) return;
    state.loading = true;
    const params = new URLSearchParams({ page: String(state.halaman), size: String(state.ukuran) });
    if (state.q) params.set("q", state.q);
    if (state.kategori) params.set("categoryId", state.kategori);

    try {
      const halaman = await api.get("/api/skills?" + params.toString());
      const isi = halaman.content || [];
      state.totalHalaman = halaman.totalPages || 1;
      gridSkill.innerHTML = append || state.halaman > 0
        ? gridSkill.innerHTML + isi.map(kartuSkill).join("")
        : isi.map(kartuSkill).join("");
      if (!isi.length && !append) {
        gridSkill.innerHTML = html`
          <div class="gk-empty gk-empty--compact">
            <h3 class="gk-empty__title">Tidak ada keahlian yang cocok</h3>
            <p class="gk-empty__desc">Coba kata kunci lain atau pilih kategori "Semua".</p>
          </div>`;
      }
      document.getElementById("hitung-keahlian").textContent =
        `${halaman.totalElements ?? isi.length} keahlian · halaman ${(halaman.number ?? 0) + 1} dari ${halaman.totalPages ?? 1}`;
      document.getElementById("muat-lagi").hidden = state.halaman + 1 >= state.totalHalaman;
    } catch (e) {
      gridSkill.innerHTML = html`<div class="gk-alert gk-alert--danger">
        Daftar keahlian tidak bisa dimuat: ${e.message}</div>`;
    } finally {
      state.loading = false;
    }
  }

  /* ---------- karya publik ---------- */
  function inisial(nama) {
    return (nama || "?").trim().split(/\s+/).slice(0, 2).map((k) => k[0]).join("").toUpperCase();
  }

  async function namaPemilik(id) {
    if (profilCache.has(id)) return profilCache.get(id);
    try {
      const p = await api.get(`/api/users/${id}`);
      profilCache.set(id, p);
      return p;
    } catch {
      const kosong = { id, fullName: "Pengguna", offers: [], averageRating: null };
      profilCache.set(id, kosong);
      return kosong;
    }
  }

  async function muatKarya() {
    const grid = document.getElementById("grid-karya");
    const kosong = document.getElementById("kosong-karya");
    try {
      const halaman = await api.get("/api/portfolios?page=0&size=12");
      const isi = halaman.content || [];
      document.getElementById("hitung-karya").textContent = `${halaman.totalElements ?? isi.length} karya`;
      if (!isi.length) {
        grid.hidden = true;
        kosong.hidden = false;
        return;
      }
      const profil = await Promise.all(isi.map((p) => namaPemilik(p.ownerId)));
      grid.innerHTML = isi.map((p, i) => {
        const pemilik = profil[i];
        const gambar = /^image\//.test(p.mimeType || "");
        return html`
          <div class="col">
            <article class="gk-card gk-card--interactive h-100">
              <div class="gk-card__thumb">
                ${gambar && state.login
                  ? html`<img src="/api/portfolios/${p.id}/content" alt="Pratinjau ${p.title}"
                              class="img-fluid" loading="lazy">`
                  : html`<span class="gk-badge gk-badge--neutral">${(p.fileName || "").split(".").pop() || "karya"}</span>`}
              </div>
              <div class="p-3">
                <h3 class="h6 mb-1">${p.title}</h3>
                <p class="text-muted small mb-1">${pemilik.fullName}
                  ${pemilik.averageRating ? html` · ★ ${pemilik.averageRating}` : ""}</p>
                <p class="text-muted mb-3" style="font-size:12px;">${p.fileName} · ${ukuran(p.fileSize)}</p>
                <button class="gk-btn gk-btn--ghost gk-btn--sm" type="button"
                        data-karya="${p.id}">Detail</button>
              </div>
            </article>
          </div>`;
      }).join("");
      grid.dataset.isi = JSON.stringify(isi.map((p, i) => ({ ...p, pemilikNama: profil[i].fullName,
        pemilikKampus: profil[i].campus, pemilikOffers: profil[i].offers })));
    } catch (e) {
      grid.innerHTML = html`<div class="col-12">
        <div class="gk-alert gk-alert--danger">Galeri karya tidak bisa dimuat: ${e.message}</div></div>`;
    }
  }

  async function bukaDetail(id) {
    const dlg = document.getElementById("detail-karya");
    const isiEl = document.getElementById("dlg-isi");
    let data = [];
    try { data = JSON.parse(document.getElementById("grid-karya").dataset.isi || "[]"); } catch { /* daftar belum siap */ }
    const p = data.find((x) => x.id === id);
    if (!p) { toast("Detail karya belum tersedia.", "err"); return; }
    document.getElementById("dlg-judul").textContent = p.title;
    const bisaLihat = state.login && p.visibility === "PUBLIC";
    isiEl.innerHTML = html`
      <p class="text-muted">${p.description || "Pemilik belum menambahkan keterangan."}</p>
      <div class="d-flex align-items-center gap-3 py-2 border-top border-bottom">
        <div class="gk-avatar gk-avatar--md">${inisial(p.pemilikNama)}</div>
        <div class="min-w-0">
          <div class="fw-semibold">${p.pemilikNama}
            <span class="text-muted small">${p.pemilikKampus || ""}</span></div>
          <div class="text-muted small">Mengajarkan: ${(p.pemilikOffers || []).join(", ") || "belum dicantumkan"}</div>
        </div>
      </div>
      <dl class="row row-cols-2 g-2 mt-3 mb-0 small">
        <div class="col"><dt class="text-muted">Berkas</dt><dd class="mb-1">${p.fileName}</dd></div>
        <div class="col"><dt class="text-muted">Ukuran</dt><dd class="mb-1">${ukuran(p.fileSize)}</dd></div>
        <div class="col"><dt class="text-muted">Tipe</dt><dd class="mb-1">${p.mimeType}</dd></div>
        <div class="col"><dt class="text-muted">Dibuat</dt><dd class="mb-1">${tanggal(p.createdAt)}</dd></div>
      </dl>
      ${/^image\//.test(p.mimeType || "") && bisaLihat
        ? html`<img class="img-fluid rounded mt-3" src="/api/portfolios/${p.id}/content"
                    alt="Pratinjau ${p.title}">`
        : ""}
      ${state.login
        ? html`<div class="d-flex flex-wrap gap-2 mt-3">
            <a class="gk-btn gk-btn--primary gk-btn--sm" href="/api/portfolios/${p.id}/content"
               target="_blank" rel="noopener">Buka berkas</a>
            <a class="gk-btn gk-btn--ghost gk-btn--sm" href="/profile">Lihat profil pemilik</a>
          </div>`
        : html`<p class="gk-alert gk-alert--info mt-3 mb-0">
            Masuk untuk membuka berkasnya dan mengajak pemiliknya barter.</p>`}
    `;
    dlg.showModal();
  }

  /* ---------- peristiwa ---------- */
  chipBox.addEventListener("click", (e) => {
    const btn = e.target.closest("[data-id]");
    if (!btn) return;
    state.kategori = btn.dataset.id;
    state.halaman = 0;
    chipBox.querySelectorAll("[data-id]").forEach((c) => c.classList.toggle("active", c === btn));
    muatKeahlian(false);
  });

  document.getElementById("form-cari").addEventListener("submit", (e) => {
    e.preventDefault();
    state.q = cari.value.trim();
    state.halaman = 0;
    muatKeahlian(false);
  });
  cari.addEventListener("input", () => {
    clearTimeout(cari._t);
    cari._t = setTimeout(() => {
      state.q = cari.value.trim();
      state.halaman = 0;
      muatKeahlian(false);
    }, 420);
  });

  document.getElementById("muat-lagi").addEventListener("click", () => {
    state.halaman += 1;
    muatKeahlian(true);
  });

  document.getElementById("grid-karya").addEventListener("click", (e) => {
    const btn = e.target.closest("[data-karya]");
    if (btn) bukaDetail(btn.dataset.karya);
  });
  document.querySelector("[data-tutup]").addEventListener("click",
    () => document.getElementById("detail-karya").close());

  /* ---------- mulai ---------- */
  api.get("/api/me").then(() => { state.login = true; }).catch(() => { state.login = false; })
    .then(() => Promise.all([muatKategori(), muatKeahlian(false), muatKarya()]));
})();
