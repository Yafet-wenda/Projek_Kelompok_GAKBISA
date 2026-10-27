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

  /* ---------- kategori ---------- */
  async function muatKategori() {
    try {
      const daftar = await api.get("/api/categories");
      chipBox.innerHTML = html`
        <button class="chip ${state.kategori ? "" : "is-on"}" type="button" data-id="">Semua kategori</button>
        ${daftar.map((c) => html`
          <button class="chip ${state.kategori === c.id ? "is-on" : ""}" type="button"
                  data-id="${c.id}" title="${c.description || ""}">${c.name}</button>`)}
      `;
    } catch (e) {
      chipBox.innerHTML = html`<span class="tiny muted">Kategori gagal dimuat (${e.message}).</span>`;
    }
  }

  /* ---------- keahlian ---------- */
  function kartuSkill(s) {
    return html`
      <li class="index__baris">
        <div class="index__atas">
          <h3>${s.name}</h3>
          <span class="index__kat">${s.category || "umum"}</span>
        </div>
        <p class="muted">${s.description || "Belum ada keterangan."}</p>
      </li>`;
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
          <li class="empty">
            <h3>Tidak ada keahlian yang cocok</h3>
            <p class="tiny">Coba kata kunci lain atau pilih kategori "Semua".</p>
          </li>`;
      }
      document.getElementById("hitung-keahlian").textContent =
        `${halaman.totalElements ?? isi.length} keahlian · halaman ${(halaman.number ?? 0) + 1} dari ${halaman.totalPages ?? 1}`;
      document.getElementById("muat-lagi").hidden = state.halaman + 1 >= state.totalHalaman;
    } catch (e) {
      gridSkill.innerHTML = html`<li class="alert alert--error">
        Daftar keahlian tidak bisa dimuat: ${e.message}</li>`;
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
          <article class="work">
            <div class="work__thumb">
              ${gambar && state.login
                ? html`<img src="/api/portfolios/${p.id}/content" alt="Pratinjau ${p.title}" loading="lazy">`
                : html`<span>${(p.fileName || "").split(".").pop() || "karya"}</span>`}
            </div>
            <div>
              <h3>${p.title}</h3>
              <p class="tiny">${pemilik.fullName}
                ${pemilik.averageRating ? html` · ★ ${pemilik.averageRating}` : ""}</p>
              <p class="work__meta">${p.fileName} · ${ukuran(p.fileSize)}</p>
            </div>
            <div class="row__side">
              <button class="btn btn--ghost btn--sm" type="button"
                      data-karya="${p.id}">Detail</button>
            </div>
          </article>`;
      }).join("");
      grid.dataset.isi = JSON.stringify(isi.map((p, i) => ({ ...p, pemilikNama: profil[i].fullName,
        pemilikKampus: profil[i].campus, pemilikOffers: profil[i].offers })));
    } catch (e) {
      grid.innerHTML = html`<div class="alert alert--error">
        Galeri karya tidak bisa dimuat: ${e.message}</div>`;
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
      <div class="stack">
        <p class="muted">${p.description || "Pemilik belum menambahkan keterangan."}</p>
        <div class="row">
          <div class="row__main">
            <div class="row__title">${p.pemilikNama}
              <span class="tiny muted">${p.pemilikKampus || ""}</span></div>
            <div class="row__meta">Mengajarkan: ${(p.pemilikOffers || []).join(", ") || "belum dicantumkan"}</div>
          </div>
        </div>
        <dl class="statlist">
          <div class="stat"><span>Berkas</span><b>${p.fileName}</b></div>
          <div class="stat"><span>Ukuran</span><b>${ukuran(p.fileSize)}</b></div>
          <div class="stat"><span>Tipe</span><b>${p.mimeType}</b></div>
          <div class="stat"><span>Dibuat</span><b>${tanggal(p.createdAt)}</b></div>
        </dl>
        ${/^image\//.test(p.mimeType || "") && bisaLihat
          ? html`<div class="iframe-wrap iframe-wrap--still">
              <img src="/api/portfolios/${p.id}/content" alt="Pratinjau ${p.title}">
            </div>`
          : ""}
        ${state.login
          ? html`<div class="btn-group">
              <a class="btn btn--leaf btn--sm" href="/api/portfolios/${p.id}/content" target="_blank" rel="noopener">Buka berkas</a>
              <a class="btn btn--ghost btn--sm" href="/dashboard">Ajak pemilik barter</a>
            </div>`
          : html`<p class="alert alert--info">Masuk untuk membuka berkasnya dan mengajak pemiliknya barter.</p>`}
      </div>`;
    dlg.showModal();
  }

  /* ---------- peristiwa ---------- */
  chipBox.addEventListener("click", (e) => {
    const btn = e.target.closest(".chip");
    if (!btn) return;
    state.kategori = btn.dataset.id;
    state.halaman = 0;
    chipBox.querySelectorAll(".chip").forEach((c) => c.classList.toggle("is-on", c === btn));
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
