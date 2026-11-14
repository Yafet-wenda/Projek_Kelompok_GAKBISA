/* ============================================================
   Dasbor GAKBISA: seluruh alur (keahlian → partner → barter →
   sesi → ulasan → karya → notifikasi → admin) lewat API REST.
   Semua sisipan teks melewati Gakbisa.html() yang meng-escape,
   jadi data pengguna tidak bisa menyuntik markup.
   ============================================================ */
(function () {
  "use strict";
  const {
    api, html, toast, statusTag, LEVEL_LABEL, tanggal, jam, relatif, ukuran,
    busy, delegate, keInstant, nilaiInputUntukSekarang,
  } = window.Gakbisa;

  const $ = (id) => document.getElementById(id);

  const state = {
    saya: null,
    offers: [], wants: [],
    kandidat: [], inbox: [], outbox: [], sesi: [],
    ulasanSaya: [], notifikasi: [], karya: [],
    daftarSkill: [], kategori: [],
    siap: {},   // tab yang sudah dimuat
  };

  const barterById = () => new Map([...state.inbox, ...state.outbox].map((b) => [b.id, b]));
  const partner = (b) => (b && b.requesterId === state.saya.id ? b.requesterName : b && b.targetUserName) || "Partner";

  /* ================= data inti ================= */

  async function muatInti() {
    state.saya = await api.get("/api/me");
    await Promise.all([
      muatSkillSaya(), muatKandidat(), muatBarter(), muatSesi(), muatNotifikasi(), muatReputasi(),
    ]);
    ["keahlian", "barter", "notifikasi"].forEach((t) => { state.siap[t] = true; });
    hitungStat();
  }

  async function muatSkillSaya() {
    const semua = await api.get("/api/me/skills");
    state.offers = semua.filter((s) => s.type === "OFFER");
    state.wants = semua.filter((s) => s.type === "WANT");
    renderKeahlian();
  }

  async function muatKandidat() {
    state.kandidat = await api.get("/api/matching/candidates?limit=8");
    renderPartner();
  }

  async function muatBarter() {
    [state.inbox, state.outbox] = await Promise.all([
      api.get("/api/barter/inbox"), api.get("/api/barter/outbox"),
    ]);
    renderBarter();
  }

  async function muatSesi() {
    state.sesi = await api.get("/api/sessions");
    renderSesi();
  }

  async function muatNotifikasi() {
    state.notifikasi = await api.get("/api/notifications");
    renderNotifikasi();
  }

  function hitungStat() {
    const aktif = [...state.inbox, ...state.outbox].filter((b) => b.status === "PENDING" || b.status === "ACCEPTED").length;
    const terjadwal = state.sesi.filter((s) => s.status === "SCHEDULED" || s.status === "ONGOING").length;
    const belum = state.notifikasi.filter((n) => !n.read).length;
    const tunggu = state.inbox.filter((b) => b.status === "PENDING").length;

    const rep = state.reputasi;
    const nilaiRep = !rep ? "menunggu…"
      : rep.averageRating ? `★ ${rep.averageRating}` : "belum ada";
    $("stat").innerHTML = html`
      ${[
        ["Reputasi", nilaiRep + (rep && rep.reviewCount ? ` · ${rep.reviewCount} ulasan` : "")],
        ["Barter aktif", aktif],
        ["Sesi terjadwal", terjadwal],
        ["Belum dibaca", belum],
      ].map(([k, v]) => html`<div class="stat"><span>${k}</span><b>${v}</b></div>`)}
    `;
    const badge = (el, n) => { el.hidden = n === 0; el.textContent = n; };
    badge($("badge-barter"), tunggu);
    badge($("badge-notif"), belum);

    const cocok = state.kandidat.length;
    $("subteks").textContent =
      cocok
        ? `${cocok} partner cocok untuk diajak gantian ajari` +
          (tunggu ? ` · ${tunggu} permintaan menunggu jawabanmu` : "") +
          (terjadwal ? ` · ${terjadwal} sesi terjadwal` : "") + "."
        : "Isi dulu daftar keahlian yang kamu ajarkan dan kamu pelajari. Rekomendasinya langsung muncul di tab ini.";
  }

  async function muatReputasi() {
    try { state.reputasi = await api.get(`/api/reviews/user/${state.saya.id}/reputation`); }
    catch { state.reputasi = { averageRating: null, reviewCount: 0, portfolioCount: 0 }; }
    hitungStat();
  }

  /* ================= render ================= */

  function renderPartner() {
    const box = $("daftar-partner");
    if (!state.kandidat.length) {
      box.innerHTML = html`<div class="empty"><h3>Belum ada rekomendasi</h3>
        <p class="tiny">Isi minimal satu keahlian "Saya ajarkan" dan satu "Saya pelajari" di tab
          <button class="chip" type="button" data-lompat="keahlian">Keahlian saya</button>.</p></div>`;
      return;
    }
    box.innerHTML = state.kandidat.map((k) => html`
      <article class="row">
        <div class="row__main">
          <div class="row__title">
            <span class="avatar" aria-hidden="true">${inisial(k.fullName)}</span>
            ${k.fullName}
            ${k.campus ? html`<span class="tiny muted">${k.campus}</span>` : ""}
          </div>
          <div class="score">
            <div class="score__bar"><i style="--w:${Math.max(0, Math.min(100, k.score))}%"></i></div>
            <span class="score__val">${k.score}</span>
          </div>
          <ul class="reasons">${(k.reasons || []).map((r) => html`<li>${r}</li>`)}</ul>
        </div>
        <div class="row__side">
          <button class="btn btn--leaf btn--sm" type="button" data-ajukan="${k.userId}"
                  data-nama="${k.fullName}">Ajukan barter</button>
        </div>
      </article>`).join("");
  }

  function renderKeahlian() {
    const isi = (kotak, daftar, jenis) => {
      const el = $(kotak);
      if (!daftar.length) {
        el.innerHTML = html`<p class="tiny muted">Belum ada. Tambahkan di form di bawah.</p>`;
        return;
      }
      el.innerHTML = daftar.map((s) => html`
        <div class="row">
          <div class="row__main">
            <div class="row__title">${s.skillName}
              <span class="tag ${jenis === "OFFER" ? "tag--offer" : "tag--want"}">${LEVEL_LABEL[s.level] || s.level}</span></div>
            <div class="row__meta">${s.category}${s.notes ? " · " + s.notes : ""}</div>
          </div>
          <div class="row__side">
            <button class="btn btn--danger btn--sm" type="button" data-hapus-skill="${s.id}">Hapus</button>
          </div>
        </div>`).join("");
    };
    isi("daftar-offer", state.offers, "OFFER");
    isi("daftar-want", state.wants, "WANT");
    isiSelectSkill();
  }

  async function isiSelectSkill() {
    const sel = $("skill-pilih");
    if (!state.daftarSkill.length) {
      try {
        const halaman = await api.get("/api/skills?size=50");
        state.daftarSkill = halaman.content || [];
      } catch { state.daftarSkill = []; }
    }
    const ada = new Set([...state.offers, ...state.wants].map((s) => s.skillId));
    const sisa = state.daftarSkill.filter((s) => !ada.has(s.id));
    sel.innerHTML = sisa.length
      ? sisa.map((s) => html`<option value="${s.id}">${s.name} · ${s.category}</option>`).join("")
      : html`<option value="">Semua keahlian sudah kamu pilih</option>`;
  }

  function renderBarter() {
    const kartu = (b, arah) => {
      const bisaTerima = arah === "masuk" && b.status === "PENDING";
      const bisaBatal = arah === "kirim" && b.status === "PENDING";
      const bisaJadwal = b.status === "ACCEPTED";
      return html`
        <div class="row">
          <div class="row__main">
            <div class="row__title">
              ${arah === "masuk" ? "Dari " + b.requesterName : "Ke " + b.targetUserName}
              ${statusTag(b.status)}
            </div>
            <div class="row__meta">${b.wantedSkill} ⇄ ${b.offeredSkill} · ${relatif(b.createdAt)}</div>
            ${b.message ? html`<p class="tiny quote">"${b.message}"</p>` : ""}
          </div>
          <div class="row__side">
            ${bisaTerima ? html`
              <button class="btn btn--leaf btn--sm" type="button" data-respons="${b.id}" data-terima="true">Terima</button>
              <button class="btn btn--danger btn--sm" type="button" data-respons="${b.id}" data-terima="false">Tolak</button>` : ""}
            ${bisaBatal ? html`<button class="btn btn--ghost btn--sm" type="button" data-batal="${b.id}">Batalkan</button>` : ""}
            ${bisaJadwal ? html`<button class="btn btn--sm" type="button" data-jadwalkan="${b.id}">Jadwalkan sesi</button>` : ""}
          </div>
        </div>`;
    };

    const kosong = (teks) => html`<div class="empty"><p class="tiny">${teks}</p></div>`;
    $("daftar-inbox").innerHTML = state.inbox.length ? state.inbox.map((b) => kartu(b, "masuk")).join("")
      : kosong("Belum ada permintaan masuk.");
    $("daftar-outbox").innerHTML = state.outbox.length ? state.outbox.map((b) => kartu(b, "kirim")).join("")
      : kosong("Belum pernah mengajak barter. Buka tab Partner untuk mengajukan.");
    hitungStat();
  }

  function renderSesi() {
    const kotak = $("daftar-sesi");
    if (!state.sesi.length) {
      kotak.innerHTML = html`<div class="empty"><h3>Belum ada sesi</h3>
        <p class="tiny">Sesi bisa dijadwalkan setelah pasangan menerima permintaan bartermu.</p></div>`;
      return;
    }
    const bnya = barterById();
    const sudah = new Set(state.ulasanSaya.map((u) => u.sessionId));
    kotak.innerHTML = state.sesi.map((s) => {
      const b = bnya.get(s.barterRequestId);
      const dengan = partner(b);
      const bisaMulai = s.status === "SCHEDULED";
      const bisaSelesai = s.status === "SCHEDULED" || s.status === "ONGOING";
      const bisaBatal = s.status !== "COMPLETED" && s.status !== "CANCELLED";
      return html`
        <div class="row">
          <div class="row__main">
            <div class="row__title">
              ${dengan ? "Sesi dengan " + dengan : "Sesi belajar"}
              ${statusTag(s.status)}
              <span class="tag">${s.meetingProvider === "JITSI" ? "Jitsi" : "Tautan luar"}</span>
            </div>
            <div class="row__meta">${tanggal(s.scheduledAt, { weekday: "long", day: "numeric", month: "long" })}
              · ${jam(s.scheduledAt)} WIB · ${s.durationMinutes} menit</div>
            ${s.meetingRoom && s.status !== "CANCELLED"
              ? html`<div class="meeting">
                  <span class="tiny">Ruang</span>
                  <code>${s.meetingRoom}</code>
                  <span class="tiny muted">hanya peserta yang melihatnya</span>
                </div>` : ""}
          </div>
          <div class="row__side">
            ${s.meetingUrl && s.status !== "CANCELLED"
              ? html`<button class="btn btn--leaf btn--sm" type="button" data-ruang="${s.id}">Masuk ruang</button>` : ""}
            ${bisaMulai ? html`<button class="btn btn--ghost btn--sm" type="button" data-status="${s.id}" data-ke="ONGOING">Mulai</button>` : ""}
            ${bisaSelesai ? html`<button class="btn btn--ghost btn--sm" type="button" data-status="${s.id}" data-ke="COMPLETED">Tandai selesai</button>` : ""}
            ${bisaSelesai && s.status === "SCHEDULED" ? html`<button class="btn btn--ghost btn--sm" type="button" data-resched="${s.id}">Geser waktu</button>` : ""}
            ${bisaBatal ? html`<button class="btn btn--danger btn--sm" type="button" data-status="${s.id}" data-ke="CANCELLED">Batalkan</button>` : ""}
            ${s.status === "COMPLETED" && !sudah.has(s.id)
              ? html`<button class="btn btn--sm" type="button" data-lompat-ulasan="${s.id}">Beri ulasan</button>` : ""}
            ${s.status === "COMPLETED" && sudah.has(s.id)
              ? html`<span class="tiny muted">sudah dinilai</span>` : ""}
          </div>
        </div>`;
    }).join("");
  }

  function renderNotifikasi() {
    const kotak = $("daftar-notifikasi");
    if (!state.notifikasi.length) {
      kotak.innerHTML = html`<div class="empty"><p class="tiny">Belum ada notifikasi.</p></div>`;
      return;
    }
    kotak.innerHTML = state.notifikasi.map((n) => html`
      <div class="row ${n.read ? "" : "unread"}">
        <div class="row__main">
          <div class="row__title">${n.title}
            <span class="tag">${labelTipe(n.type)}</span></div>
          <div class="row__meta">${n.body}</div>
        </div>
        <div class="row__side">
          <span class="tiny muted">${relatif(n.createdAt)}</span>
          ${n.read ? "" : html`<button class="btn btn--ghost btn--sm" type="button" data-baca="${n.id}">Tandai terbaca</button>`}
        </div>
      </div>`).join("");
    hitungStat();
  }

  const labelTipe = (t) => ({ BARTER: "Barter", SESSION: "Sesi", REVIEW: "Ulasan", SYSTEM: "Sistem" }[t] || t);

  function renderUlasan() {
    const bnya = barterById();
    const sudah = new Set(state.ulasanSaya.map((u) => u.sessionId));
    const tunggu = state.sesi.filter((s) => s.status === "COMPLETED" && !sudah.has(s.id));
    const kotak = $("daftar-ulasan-pending");

    kotak.innerHTML = tunggu.length ? tunggu.map((s) => {
      const b = bnya.get(s.barterRequestId);
      return html`
        <div class="row row--stack">
          <div class="row__title">Sesi dengan ${partner(b)}
            <span class="tiny muted">${tanggal(s.completedAt || s.scheduledAt)}</span></div>
          <form data-form-ulasan="${s.id}">
            <div class="star-pick" role="group" aria-label="Nilai 1 sampai 5">
              ${[1, 2, 3, 4, 5].map((n) => html`
                <button type="button" data-star="${n}" aria-pressed="false" aria-label="${n} bintang">★</button>`)}
            </div>
            <div class="field field--tight">
              <label class="lbl" for="kom-${s.id}">Komentar</label>
              <textarea id="kom-${s.id}" name="comment" maxlength="2000"
                        placeholder="Apa yang membuat sesi ini berguna?"></textarea>
            </div>
            <button class="btn btn--leaf btn--sm" type="submit">Kirim ulasan</button>
          </form>
        </div>`;
    }).join("")
      : html`<p class="tiny muted">Tidak ada sesi yang menunggu dinilai.</p>`;
  }

  async function muatUlasanMasuk() {
    const daftar = await api.get(`/api/reviews/user/${state.saya.id}`);
    const kotak = $("daftar-ulasan-masuk");
    if (!daftar.length) {
      kotak.innerHTML = html`<p class="tiny muted">Belum ada yang menilai kamu. Selesaikan satu sesi dulu.</p>`;
      return;
    }
    kotak.innerHTML = daftar.map((r) => html`
      <div class="row">
        <div class="row__main">
          <div class="row__title"><span class="stars" role="img" aria-label="Nilai ${r.rating} dari 5">${bintang(r.rating)}</span></div>
          <div class="row__meta">${r.comment || "Tanpa komentar"} · ${relatif(r.createdAt)}</div>
        </div>
      </div>`).join("");
  }

  const bintang = (n) => Array.from({ length: 5 }, (_, i) => (i < n ? html`<b>★</b>` : html`<span>★</span>`));

  async function muatKarya() {
    const daftar = await api.get(`/api/portfolios/user/${state.saya.id}`);
    state.karya = daftar || [];
    const kotak = $("daftar-karya");
    if (!state.karya.length) {
      kotak.innerHTML = html`<p class="tiny muted">Belum ada karya terunggah.</p>`;
      return;
    }
    kotak.innerHTML = state.karya.map((p) => html`
      <div class="row">
        <div class="row__main">
          <div class="row__title">${p.title}
            <span class="tag">${p.visibility === "PUBLIC" ? "Publik" : "Privat"}</span></div>
          <div class="row__meta">${p.fileName} · ${ukuran(p.fileSize)} · ${tanggal(p.createdAt)}</div>
        </div>
        <div class="row__side">
          <a class="btn btn--ghost btn--sm" href="/api/portfolios/${p.id}/content" target="_blank" rel="noopener">Lihat</a>
          <button class="btn btn--danger btn--sm" type="button" data-hapus-karya="${p.id}">Hapus</button>
        </div>
      </div>`).join("");
  }

  /* ================= admin ================= */

  async function muatAdmin() {
    const [ringkas, pengguna, laporan, kategori] = await Promise.all([
      api.get("/api/admin/summary"), api.get("/api/admin/users"),
      api.get("/api/admin/reports"), api.get("/api/categories"),
    ]);
    state.kategori = kategori;
    $("admin-statistik").innerHTML = Object.entries(ringkas).map(([k, v]) =>
      html`<div class="stat"><span>${labelAdmin(k)}</span><b>${v}</b></div>`).join("");

    $("daftar-pengguna").innerHTML = pengguna.map((u) => html`
      <div class="row">
        <div class="row__main">
          <div class="row__title">${u.fullName}
            <span class="tag">${u.role}</span>
            ${u.status === "SUSPENDED"
              ? html`<span class="status" data-s="CANCELLED">Nonaktif</span>`
              : html`<span class="status" data-s="COMPLETED">Aktif</span>`}</div>
          <div class="row__meta">${u.email}</div>
        </div>
        <div class="row__side">
          <button class="btn btn--sm ${u.status === "ACTIVE" ? "btn--danger" : "btn--leaf"}" type="button"
                  data-status-user="${u.id}" data-ke="${u.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE"}">
            ${u.status === "ACTIVE" ? "Nonaktifkan" : "Aktifkan"}
          </button>
        </div>
      </div>`).join("");

    $("daftar-laporan").innerHTML = laporan.length ? laporan.map((r) => html`
      <div class="row">
        <div class="row__main">
          <div class="row__title">${r.targetType} · ${statusTag(r.status)}</div>
          <div class="row__meta">${r.reason} · ${relatif(r.createdAt)}</div>
        </div>
        <div class="row__side">
          <button class="btn btn--leaf btn--sm" type="button" data-putuskan="${r.id}" data-ke="RESOLVED"
                  data-hide="${r.targetType === "PORTFOLIO" ? "1" : "0"}">Tindak lanjuti</button>
          <button class="btn btn--ghost btn--sm" type="button" data-putuskan="${r.id}" data-ke="DISMISSED">Abaikan</button>
        </div>
      </div>`).join("")
      : html`<p class="tiny muted">Tidak ada laporan terbuka.</p>`;

    $("ahli-kategori").innerHTML = kategori.map((c) => html`<option value="${c.id}">${c.name}</option>`).join("");
  }

  const labelAdmin = (k) => ({
    users: "Pengguna", students: "Mahasiswa", portfolios: "Portofolio",
    barterRequests: "Barter", sessionsCompleted: "Sesi selesai", openReports: "Laporan terbuka",
  }[k] || k);

  /* ================= dialog ================= */

  function bukaDialogBarter(userId, nama) {
    if (!state.offers.length || !state.wants.length) {
      toast("Isi dulu daftar " + (state.offers.length ? "keahlian yang ingin kamu pelajari" : "keahlian yang kamu ajarkan") + ".", "err");
      tampilTab("keahlian");
      return;
    }
    $("dlg-barter-judul").textContent = `Ajukan barter ke ${nama}`;
    $("barter-target").value = userId;
    $("barter-offer").innerHTML = state.offers.map((s) =>
      html`<option value="${s.id}">${s.skillName} · ${LEVEL_LABEL[s.level] || s.level}</option>`).join("");
    $("barter-want").innerHTML = state.wants.map((s) =>
      html`<option value="${s.id}">${s.skillName}</option>`).join("");
    $("barter-pesan").value = "";
    $("dlg-barter").showModal();
  }

  function bukaDialogSesi(barterId, nama) {
    state.modeSesi = "baru";
    $("sesi-barter").value = barterId;
    $("dlg-sesi-judul").textContent = "Jadwalkan sesi";
    $("sesi-partner").textContent = `Sesi pertama dengan ${nama}. Ruang video dibuat otomatis oleh server.`;
    $("sesi-waktu").value = nilaiInputUntukSekarang(15);
    $("sesi-durasi").value = 60;
    $("sesi-eksternal").closest(".field").hidden = false;
    $("dlg-sesi").showModal();
  }

  function bukaDialogGeser(sesiId) {
    const s = state.sesi.find((x) => x.id === sesiId);
    if (!s) return;
    state.modeSesi = "geser";
    $("sesi-barter").value = s.barterRequestId;
    state.sesiTarget = sesiId;
    $("dlg-sesi-judul").textContent = "Geser waktu sesi";
    $("sesi-partner").textContent = "Jam baru harus masih di masa depan; tautan ruang tetap sama.";
    $("sesi-waktu").value = dariIsoKeInput(s.scheduledAt);
    $("sesi-durasi").value = s.durationMinutes;
    $("sesi-eksternal").closest(".field").hidden = true;
    $("dlg-sesi").showModal();
  }

  /** ISO UTC -> nilai input datetime-local (waktu lokal). */
  function dariIsoKeInput(iso) {
    const d = new Date(iso);
    if (isNaN(d)) return "";
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function sematkanRuang(id) {
    const s = state.sesi.find((x) => x.id === id);
    if (!s || !s.meetingUrl) return;
    const wrap = $("ruangan");
    $("ruangan-judul").textContent = "Ruang " + (s.meetingRoom || "meeting");
    $("ruangan-frame").src = s.meetingUrl;
    wrap.hidden = false;
    wrap.scrollIntoView({ behavior: "smooth", block: "center" });
    if (s.status === "SCHEDULED") ubahStatus(id, "ONGOING");
  }

  /* ================= tindakan ================= */

  async function aksi(btn, fn, pesanSukses) {
    busy(btn, true);
    try {
      await fn();
      if (pesanSukses) toast(pesanSukses, "ok");
    } catch (e) {
      toast(e.message, "err", 5200);
    } finally {
      busy(btn, false);
    }
  }

  async function responsBarter(id, terima) {
    await api.post(`/api/barter/${id}/respond`, { accept: terima });
    await muatBarter();
    if (terima) await muatSesi();
  }

  async function batalkanBarter(id) {
    await api.post(`/api/barter/${id}/cancel`);
    await muatBarter();
  }

  async function jadwalkan(barterId, waktu, durasi, eksternal) {
    await api.post("/api/sessions", {
      barterRequestId: barterId, scheduledAt: waktu, durationMinutes: durasi,
      externalMeetingUrl: eksternal || null,
    });
    await muatSesi();
    await muatNotifikasi();
  }

  async function ubahStatus(id, ke) {
    await api.post(`/api/sessions/${id}/status`, { status: ke });
    await muatSesi();
    await muatNotifikasi();
    hitungStat();
  }

  async function kirimUlasan(sessionId, rating, comment, btn) {
    if (!rating) { toast("Pilih dulu nilainya, 1 sampai 5.", "err"); return; }
    await api.post("/api/reviews", { learningSessionId: sessionId, rating, comment: comment || null });
    await muatUlasanSaya();
    renderUlasan();
    await muatNotifikasi();
    void btn;
  }

  async function muatUlasanSaya() {
    try { state.ulasanSaya = await api.get("/api/reviews/mine"); }
    catch { state.ulasanSaya = []; }
  }

  function inisial(nama) {
    return (nama || "?").trim().split(/\s+/).slice(0, 2).map((k) => k[0]).join("").toUpperCase();
  }

  /* ================= tab ================= */

  function tampilTab(nama) {
    document.querySelectorAll(".tabs button").forEach((b) => {
      const aktif = b.dataset.tab === nama;
      b.classList.toggle("is-on", aktif);
      b.setAttribute("aria-selected", String(aktif));
      b.tabIndex = aktif ? 0 : -1;
    });
    document.querySelectorAll(".panel").forEach((p) =>
      p.hidden = p.id !== "panel-" + nama);
    history.replaceState(null, "", "#" + nama);
    muatTab(nama);
  }

  async function muatTab(nama) {
    if (state.siap[nama]) return;
    state.siap[nama] = true;
    try {
      if (nama === "ulasan") { await muatUlasanSaya(); renderUlasan(); await muatUlasanMasuk(); }
      if (nama === "karya") await muatKarya();
      if (nama === "admin") await muatAdmin();
      if (nama === "barter") await muatBarter();
      if (nama === "keahlian") await muatSkillSaya();
      if (nama === "notifikasi") await muatNotifikasi();
    } catch (e) {
      state.siap[nama] = false;
      toast(e.message, "err");
    }
  }

  /* ================= peristiwa ================= */

  document.querySelector(".tabs").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-tab]");
    if (b) tampilTab(b.dataset.tab);
  });

  // Hanya tab aktif yang ikut urutan Tab, jadi panah dipakai untuk berpindah.
  document.querySelector(".tabs").addEventListener("keydown", (e) => {
    const daftar = [...document.querySelectorAll(".tabs button")];
    const i = daftar.indexOf(e.target.closest("button[data-tab]"));
    if (i < 0) return;
    const arah = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    const tujuan = e.key === "Home" ? 0
      : e.key === "End" ? daftar.length - 1
      : arah === undefined ? -1
      : (i + arah + daftar.length) % daftar.length;
    if (tujuan < 0) return;
    e.preventDefault();
    tampilTab(daftar[tujuan].dataset.tab);
    daftar[tujuan].focus();
  });

  // Tombol back/forward browser mengubah hash tanpa memuat ulang halaman.
  addEventListener("hashchange", () => {
    const nama = location.hash.slice(1);
    const aktif = document.querySelector(".tabs button.is-on")?.dataset.tab;
    if (nama && nama !== aktif && $("panel-" + nama)) tampilTab(nama);
  });

  document.addEventListener("click", (e) => {
    const lompat = e.target.closest("[data-lompat]");
    if (lompat) tampilTab(lompat.dataset.lompat);
    const reload = e.target.closest("[data-muat]");
    if (reload) {
      const bagian = reload.dataset.muat;
      busy(reload, true);
      (bagian === "notifikasi" ? muatNotifikasi() : muatKandidat())
        .catch((err) => toast(err.message, "err"))
        .finally(() => busy(reload, false));
    }
    const keUlasan = e.target.closest("[data-lompat-ulasan]");
    if (keUlasan) { tampilTab("ulasan"); }
  });

  delegate($("daftar-partner"), "click", "[data-ajukan]",
    (e, btn) => bukaDialogBarter(btn.dataset.ajukan, btn.dataset.nama));

  delegate($("panel-keahlian"), "click", "[data-hapus-skill]", async (e, btn) => {
    await aksi(btn, async () => {
      await api.del(`/api/me/skills/${btn.dataset.hapusSkill}`);
      await muatSkillSaya();
      await muatKandidat();
    }, "Keahlian dilepas dari daftar.");
  });

  $("form-skill").addEventListener("submit", (e) => {
    e.preventDefault();
    const btn = e.submitter || e.target.querySelector("button");
    const body = {
      skillId: $("skill-pilih").value,
      type: $("skill-type").value,
      level: $("skill-level").value,
    };
    if (!body.skillId) { toast("Pilih keahlian dulu.", "err"); return; }
    aksi(btn, async () => {
      await api.post("/api/me/skills", body);
      await muatSkillSaya();
      await muatKandidat();
      $("form-skill").reset();
    }, "Keahlian ditambahkan.");
  });

  delegate($("daftar-inbox"), "click", "[data-respons]", (e, btn) =>
    aksi(btn, () => responsBarter(btn.dataset.respons, btn.dataset.terima === "true"),
      btn.dataset.terima === "true" ? "Permintaan diterima. Jadwalkan sesinya di tab Sesi." : "Permintaan ditolak."));

  delegate($("daftar-inbox"), "click", "[data-jadwalkan]", (e, btn) => {
    const b = barterById().get(btn.dataset.jadwalkan);
    bukaDialogSesi(btn.dataset.jadwalkan, partner(b));
  });
  delegate($("daftar-outbox"), "click", "[data-jadwalkan]", (e, btn) => {
    const b = barterById().get(btn.dataset.jadwalkan);
    bukaDialogSesi(btn.dataset.jadwalkan, partner(b));
  });
  delegate($("daftar-outbox"), "click", "[data-batal]", (e, btn) =>
    aksi(btn, () => batalkanBarter(btn.dataset.batal), "Permintaan dibatalkan."));

  $("kirim-barter").addEventListener("click", (e) => {
    const btn = e.currentTarget;
    aksi(btn, async () => {
      await api.post("/api/barter", {
        targetUserId: $("barter-target").value,
        offeredUserSkillId: $("barter-offer").value,
        wantedUserSkillId: $("barter-want").value,
        message: $("barter-pesan").value.trim() || null,
      });
      $("dlg-barter").close();
      await muatBarter();
      tampilTab("barter");
    }, "Permintaan barter dikirim.");
  });

  document.querySelectorAll("[data-batal], [data-batal-sesi]").forEach((b) =>
    b.addEventListener("click", () => {
      $("dlg-barter").close();
      $("dlg-sesi").close();
    }));

  delegate($("daftar-sesi"), "click", "[data-ruang]", (e, btn) => sematkanRuang(btn.dataset.ruang));
  $("ruangan-tutup").addEventListener("click", () => {
    $("ruangan").hidden = true;
    $("ruangan-frame").src = "about:blank";
  });
  delegate($("daftar-sesi"), "click", "[data-status]", (e, btn) =>
    aksi(btn, () => ubahStatus(btn.dataset.status, btn.dataset.ke),
      { ONGOING: "Sesi ditandai sedang berjalan.", COMPLETED: "Sesi ditandai selesai.", CANCELLED: "Sesi dibatalkan." }[btn.dataset.ke]));
  delegate($("daftar-sesi"), "click", "[data-resched]", (e, btn) => bukaDialogGeser(btn.dataset.resched));

  $("kirim-sesi").addEventListener("click", (e) => {
    const btn = e.currentTarget;
    const waktu = keInstant($("sesi-waktu").value);
    const durasi = Number($("sesi-durasi").value) || 60;
    if (!waktu) { toast("Isi waktu sesi dulu.", "err"); return; }
    if (new Date(waktu).getTime() < Date.now()) { toast("Pilih waktu di masa depan.", "err"); return; }
    const menggeser = state.modeSesi === "geser";
    aksi(btn, async () => {
      if (menggeser) {
        await api.patch(`/api/sessions/${state.sesiTarget}?at=${encodeURIComponent(waktu)}&durationMinutes=${durasi}`);
        await muatSesi();
      } else {
        await jadwalkan($("sesi-barter").value, waktu, durasi, $("sesi-eksternal").value.trim());
        tampilTab("sesi");
      }
      $("dlg-sesi").close();
      $("sesi-eksternal").value = "";
    }, menggeser ? "Jadwal sesi digeser." : "Sesi dijadwalkan, ruang videonya sudah jadi.");
  });

  delegate($("panel-ulasan"), "click", "[data-star]", (e, btn) => {
    const nilai = Number(btn.dataset.star);
    btn.closest(".star-pick").querySelectorAll("button").forEach((b) => {
      const aktif = Number(b.dataset.star) <= nilai;
      b.classList.toggle("is-on", aktif);
      b.setAttribute("aria-pressed", String(b === btn));
    });
    btn.closest("form").dataset.rating = nilai;
  });

  delegate($("panel-ulasan"), "submit", "[data-form-ulasan]", (e, form) => {
    e.preventDefault();
    const btn = form.querySelector('button[type="submit"]');
    aksi(btn, () => kirimUlasan(form.dataset.formUlasan, Number(form.dataset.rating || 0),
      form.querySelector("textarea").value.trim(), btn), "Ulasan terkirim. Terima kasih!");
  });

  $("form-karya").addEventListener("submit", (e) => {
    e.preventDefault();
    const btn = e.submitter || e.target.querySelector('button[type="submit"]');
    const fd = new FormData();
    fd.append("file", $("karya-file").files[0]);
    fd.append("title", $("karya-judul").value.trim());
    if ($("karya-deskripsi").value.trim()) fd.append("description", $("karya-deskripsi").value.trim());
    fd.append("visibility", $("karya-visibilitas").value);
    if (!$("karya-file").files[0]) { toast("Pilih berkas dulu.", "err"); return; }
    aksi(btn, async () => {
      await api.postForm("/api/portfolios", fd);
      $("form-karya").reset();
      await muatKarya();
    }, "Karya terunggah dan tampil di katalog.");
  });

  delegate($("daftar-karya"), "click", "[data-hapus-karya]", (e, btn) => {
    if (!confirm("Hapus karya ini dari portofolio?")) return;
    aksi(btn, async () => {
      await api.del(`/api/portfolios/${btn.dataset.hapusKarya}`);
      await muatKarya();
    }, "Karya dihapus.");
  });

  delegate($("daftar-notifikasi"), "click", "[data-baca]", (e, btn) =>
    aksi(btn, async () => {
      await api.post(`/api/notifications/${btn.dataset.baca}/read`);
      await muatNotifikasi();
    }, null));

  $("form-kategori").addEventListener("submit", (e) => {
    e.preventDefault();
    aksi(e.submitter, async () => {
      await api.post("/api/admin/categories", { name: $("kat-nama").value.trim(), description: $("kat-desk").value.trim() });
      $("form-kategori").reset();
      await muatAdmin();
    }, "Kategori disimpan.");
  });

  $("form-ahli").addEventListener("submit", (e) => {
    e.preventDefault();
    aksi(e.submitter, async () => {
      await api.post("/api/admin/skills", {
        name: $("ahli-nama").value.trim(),
        categoryId: $("ahli-kategori").value,
        description: "",
      });
      $("form-ahli").reset();
      state.daftarSkill = [];
      await isiSelectSkill();
      await muatAdmin();
    }, "Keahlian disimpan.");
  });

  delegate($("daftar-pengguna"), "click", "[data-status-user]", (e, btn) => {
    if (!confirm(`${btn.textContent.trim()} akun ini?`)) return;
    aksi(btn, async () => {
      await api.post(`/api/admin/users/${btn.dataset.statusUser}/status`, { status: btn.dataset.ke });
      await muatAdmin();
    }, "Status akun diubah.");
  });

  delegate($("daftar-laporan"), "click", "[data-putuskan]", (e, btn) =>
    aksi(btn, async () => {
      await api.post(`/api/admin/reports/${btn.dataset.putuskan}/review`, {
        decision: btn.dataset.ke,
        hidePortfolio: btn.dataset.hide === "1" && btn.dataset.ke === "RESOLVED",
        suspendUser: false,
      });
      await muatAdmin();
    }, "Laporan diproses."));

  /* ================= mulai ================= */

  muatInti()
    .then(() => {
      const awal = (location.hash || "#partner").slice(1);
      tampilTab($("panel-" + awal) ? awal : "partner");
    })
    .catch((e) => {
      toast(e.message || "Gagal memuat dasbor.", "err", 8000);
      $("daftar-partner").innerHTML = html`<div class="alert alert--error">
        Dasbor tidak bisa dimuat: ${e.message}</div>`;
    });
})();
