/* Beranda: tampilkan kategori keahlian asli dari API (halaman ini boleh dibuka tamu). */
(function () {
  "use strict";
  const { api, html, toast } = window.Gakbisa;
  const strip = document.getElementById("kategori-strip");
  if (!strip) return;

  api.get("/api/categories")
    .then((daftar) => {
      if (!Array.isArray(daftar) || !daftar.length) {
        strip.innerHTML = html`<span class="tiny muted">Belum ada kategori terdaftar.</span>`;
        return;
      }
      strip.innerHTML = html`
        ${daftar.map((c) => html`
          <a class="chip" href="/catalog?kategori=${c.id}" title="${c.description || ""}">${c.name}</a>`)}
      `;
    })
    .catch((e) => {
      strip.innerHTML = html`<span class="tiny muted">Kategori gagal dimuat: ${e.message}</span>`;
      toast("Gagal memuat kategori dari server.", "err");
    });
})();
