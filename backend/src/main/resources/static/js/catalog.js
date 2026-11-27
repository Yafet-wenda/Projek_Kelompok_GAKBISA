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
  const CHIP = "chip";

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
      chipBox.innerHTML = html`<span class="muted small">Kategori gagal dimuat (${e.message}).</span>`;
    }
  }

  /* ---------- keahlian ---------- */
  function kartuSkill(s) {
    return html`
      <div class="list-item">
        <div class="item-head">
          <div style="min-width:0;">
            <div class="item-title">${s.name}</div>
            ${s.description ? html`<div class="item-body">${s.description}</div>` : ""}
          </div>
          ${s.category ? html`<span class="tag tag--neutral">${s.category}</span>` : ""}
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
          <div class="list-item muted small">
            Tidak ada yang cocok. Hapus kata kunci atau kembali ke "Semua kategori".
          </div>`;
      }
      const total = halaman.totalElements ?? isi.length;
      const halamanIni = (halaman.number ?? 0) + 1;
      const dari = (halaman.totalPages ?? 1);
      document.getElementById("hitung-keahlian").textContent =
        dari > 1 ? `${total} keahlian, halaman ${halamanIni} dari ${dari}` : `${total} keahlian`;
      document.getElementById("muat-lagi").hidden = state.halaman + 1 >= state.totalHalaman;
    } catch (e) {
      gridSkill.innerHTML = html`<div class="notice notice--danger" style="margin:20px;">
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
      const totalKarya = halaman.totalElements ?? isi.length;
      document.getElementById("hitung-karya").textContent = totalKarya ? `${totalKarya} karya` : "";
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
          <article class="work-card">
            <div class="work-card__thumb">
              ${gambar && p.visibility === "PUBLIC"
                ? html`<img src="/web/portfolios/${p.id}/content" alt="Pratinjau ${p.title}" loading="lazy">`
                : html`<span class="muted mini" style="padding:0 14px;text-align:center;">${p.fileName}</span>`}
            </div>
            <div class="work-card__body">
              <h3 class="work-card__title">${p.title}</h3>
              <p class="work-card__desc">oleh ${pemilik.fullName}
                ${pemilik.averageRating ? html` &middot; ${pemilik.averageRating} / 5` : ""}</p>
              <div class="work-card__actions">
                <button class="btn btn--line btn--sm" type="button" data-karya="${p.id}">Lihat detail</button>
              </div>
            </div>
          </article>`;
      }).join("");
      grid.dataset.isi = JSON.stringify(isi.map((p, i) => ({ ...p, pemilikNama: profil[i].fullName,
        pemilikKampus: profil[i].campus, pemilikOffers: profil[i].offers })));
    } catch (e) {
      grid.innerHTML = html`<div style="grid-column:1/-1;">
        <div class="notice notice--danger">Galeri karya tidak bisa dimuat: ${e.message}</div></div>`;
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
    const bisaLihat = p.visibility === "PUBLIC";
    isiEl.innerHTML = html`
      <p class="muted">${p.description || "Pemilik belum menambahkan keterangan."}</p>
      <div class="person" style="padding:14px 0;border-top:1px solid var(--line-soft);border-bottom:1px solid var(--line-soft);">
        <span class="avatar avatar--sm">${inisial(p.pemilikNama)}</span>
        <div class="person__body">
          <div class="person__name">${p.pemilikNama}</div>
          <div class="person__meta">${p.pemilikKampus || "Kampus belum diisi"}</div>
        </div>
      </div>
      <p class="small muted" style="margin-top:12px;">Mengajarkan:
        ${(p.pemilikOffers || []).join(", ") || "belum dicantumkan"}</p>
      <dl class="kv" style="margin-top:14px;">
        ${[["Berkas", p.fileName], ["Ukuran", ukuran(p.fileSize)],
           ["Tipe berkas", p.mimeType], ["Diunggah", tanggal(p.createdAt)]]
          .map(([k, v]) => html`
            <div><dt>${k}</dt><dd>${v}</dd></div>`)}
      </dl>
      ${/^image\//.test(p.mimeType || "") && bisaLihat
        ? html`<img class="img-fluid rounded" style="margin-top:14px;"
                    src="/web/portfolios/${p.id}/content" alt="Pratinjau ${p.title}">`
        : ""}
      ${/^video\/(mp4|webm)$/.test(p.mimeType || "") && bisaLihat
        ? html`<video class="img-fluid rounded" style="margin-top:14px;" controls preload="metadata"
                      src="/web/portfolios/${p.id}/content" aria-label="${p.title}"></video>`
        : ""}
      ${bisaLihat
        ? html`<div class="wrap" style="margin-top:16px;">
            <a class="btn btn--dark btn--sm" href="/web/portfolios/${p.id}/content"
               target="_blank" rel="noopener noreferrer">Buka berkas</a>
            <a class="btn btn--line btn--sm" href="/web/portfolios/${p.id}/content"
               download="${p.fileName}">Unduh</a>
            <a class="btn btn--ghost btn--sm" href="/profile/${p.ownerId}">Profil pemilik</a>
          </div>`
        : html`<div class="notice notice--info" style="margin-top:16px;">Berkas ini tidak tersedia untuk publik.</div>`}
      ${!state.login ? html`<p class="muted small" style="margin-top:14px;">Masuk untuk mengajak pemiliknya barter.</p>` : ""}
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
  document.querySelectorAll("[data-tutup]").forEach((btn) =>
    btn.addEventListener("click", () => document.getElementById("detail-karya").close()));

  /* ---------- mulai ---------- */
  api.get("/web/api/me").then(() => { state.login = true; }).catch(() => { state.login = false; })
    .then(() => Promise.all([muatKategori(), muatKeahlian(false), muatKarya()]));
})();
