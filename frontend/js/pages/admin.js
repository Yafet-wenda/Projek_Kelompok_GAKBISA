import { api } from "../api.js";
import { esc, fmtDate, toast, alertBox } from "../ui.js";

export default async function page(view) {
  const [summary, reports, users, categories, disputes, campusRequests, chatReports, feedback] = await Promise.all([
    api("/api/admin/summary").catch(() => ({})),
    api("/api/admin/reports", { query: { status: "OPEN" } }).catch(() => []),
    api("/api/admin/users").catch(() => []),
    api("/api/admin/categories").catch(() => []),
    api("/api/disputes/admin").catch(() => []),
    api("/api/admin/campus-verifications").catch(() => []),
    api("/api/admin/chat-reports").catch(() => []),
    api("/api/feedback/admin").catch(() => []),
  ]);

  let activeTab = "reports"; // reports | disputes | users | categories | chat

  function render() {
    view.innerHTML = `
      <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-4">
        <div>
          <h1 class="h4 fw-bold m-0"><i class="bi bi-shield-check text-primary me-2"></i>Panel Administrasi &amp; Moderasi</h1>
          <p class="small text-muted mb-0">Kelola pengguna, tinjau laporan pelanggaran, dan kurasi katalog keahlian.</p>
        </div>
      </div>
      <div id="admin-msg"></div>

      <!-- Ringkasan Statistik -->
      <div class="row g-3 mb-4">
        <div class="col-6 col-md-4 col-lg-2">
          <div class="gk-card p-3 text-center">
            <div class="small text-muted">Pengguna</div>
            <div class="fs-4 fw-bold text-primary">${esc(summary.users ?? 0)}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="gk-card p-3 text-center">
            <div class="small text-muted">Mahasiswa</div>
            <div class="fs-4 fw-bold" style="color:var(--gk-ink)">${esc(summary.students ?? 0)}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="gk-card p-3 text-center">
            <div class="small text-muted">Portofolio</div>
            <div class="fs-4 fw-bold" style="color:var(--gk-ink)">${esc(summary.portfolios ?? 0)}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="gk-card p-3 text-center">
            <div class="small text-muted">Permintaan Barter</div>
            <div class="fs-4 fw-bold" style="color:var(--gk-ink)">${esc(summary.barterRequests ?? 0)}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="gk-card p-3 text-center">
            <div class="small text-muted">Sesi Selesai</div>
            <div class="fs-4 fw-bold text-success">${esc(summary.sessionsCompleted ?? 0)}</div>
          </div>
        </div>
        <div class="col-6 col-md-4 col-lg-2">
          <div class="gk-card p-3 text-center">
            <div class="small text-muted">Laporan Masuk</div>
            <div class="fs-4 fw-bold ${summary.openReports ? "text-danger" : "text-muted"}">${esc(summary.openReports ?? 0)}</div>
          </div>
        </div>
      </div>

      <!-- Navigasi Tab Admin -->
      <ul class="nav nav-pills mb-3 gap-2" role="tablist">
        <li class="nav-item">
          <button class="nav-link ${activeTab === "reports" ? "active" : ""}" data-tab="reports">
            <i class="bi bi-exclamation-triangle me-1"></i>Moderasi Laporan (${reports.length})
          </button>
        </li>
        <li class="nav-item">
          <button class="nav-link ${activeTab === "users" ? "active" : ""}" data-tab="users">
            <i class="bi bi-people me-1"></i>Kelola Pengguna (${users.length})
          </button>
        </li>
        <li class="nav-item"><button class="nav-link ${activeTab === "disputes" ? "active" : ""}" data-tab="disputes"><i class="bi bi-shield-exclamation me-1"></i>Sengketa (${disputes.length})</button></li>
        <li class="nav-item"><button class="nav-link ${activeTab === "verification" ? "active" : ""}" data-tab="verification"><i class="bi bi-patch-check me-1"></i>Verifikasi kampus (${campusRequests.length})</button></li>
        <li class="nav-item"><button class="nav-link ${activeTab === "chat" ? "active" : ""}" data-tab="chat"><i class="bi bi-chat-left-text me-1"></i>Laporan chat (${chatReports.length})</button></li>
        <li class="nav-item"><button class="nav-link ${activeTab === "feedback" ? "active" : ""}" data-tab="feedback"><i class="bi bi-chat-square-heart me-1"></i>Masukan aplikasi (${feedback.length})</button></li>
        <li class="nav-item">
          <button class="nav-link ${activeTab === "categories" ? "active" : ""}" data-tab="categories">
            <i class="bi bi-folder me-1"></i>Kategori &amp; Keahlian (${categories.length})
          </button>
        </li>
      </ul>

      <!-- Konten Tab -->
      <div id="tab-content">
        ${activeTab === "reports" ? renderReportsTab() : activeTab === "disputes" ? renderDisputesTab() : activeTab === "verification" ? renderVerificationTab() : activeTab === "chat" ? renderChatReports() : activeTab === "feedback" ? renderFeedback() : activeTab === "users" ? renderUsersTab() : renderCategoriesTab()}
      </div><dialog id="dlg-resolve-dispute" class="gk-dialog"></dialog>`;

    attachEvents();
  }

  function renderReportsTab() {
    if (!reports.length) {
      return `
        <div class="gk-card p-4 text-center text-muted">
          <i class="bi bi-check-circle-fill text-success fs-1 mb-2"></i>
          <p class="mb-0">Tidak ada laporan terbuka yang perlu ditinjau. Semua tertangani dengan baik!</p>
        </div>`;
    }
    return `
      <div class="table-responsive">
        <table class="table table-hover align-middle gk-card">
          <thead class="table-light">
            <tr>
              <th>Target</th>
              <th>Alasan Pelaporan</th>
              <th>Status</th>
              <th class="text-end">Aksi Moderator</th>
            </tr>
          </thead>
          <tbody>
            ${reports.map((r) => `
              <tr>
                <td>
                  <span class="badge ${r.targetType === "USER" ? "bg-primary" : "bg-warning text-dark"} me-1">
                    ${esc(r.targetType)}
                  </span>
                  ${r.targetType === "USER" ? `
                    <a href="#/profile/${esc(r.targetId)}" target="_blank" class="small fw-bold">Lihat Profil &rarr;</a>` : `
                    <span class="small font-monospace text-muted">${esc(r.targetId)}</span>`}
                </td>
                <td><div class="small fw-medium">${esc(r.reason)}</div></td>
                <td><span class="badge bg-danger rounded-pill">${esc(r.status)}</span></td>
                <td class="text-end">
                  <div class="btn-group btn-group-sm">
                    <button class="btn btn-outline-secondary" data-dismiss-report="${esc(r.id)}">
                      Abaikan
                    </button>
                    <button class="btn btn-danger" data-action-report="${esc(r.id)}" data-target-type="${esc(r.targetType)}">
                      Tindak &amp; Tangguhkan
                    </button>
                  </div>
                </td>
              </tr>`).join("")}
          </tbody>
        </table>
      </div>`;
  }

  function renderDisputesTab() {
    if (!disputes.length) {
      return `
        <div class="gk-card p-4 text-center text-muted">
          <i class="bi bi-shield-check text-success fs-1 mb-2"></i>
          <p class="mb-0">Tidak ada sengketa barter aktif saat ini. Semua berjalan tertib!</p>
        </div>`;
    }
    return `
      <div class="table-responsive">
        <table class="table table-hover align-middle gk-card">
          <thead class="table-light">
            <tr>
              <th>Pelapor &amp; Terlapor</th>
              <th>Alasan Sengketa</th>
              <th>Status</th>
              <th>Hasil / Solusi</th>
              <th class="text-end">Aksi Admin</th>
            </tr>
          </thead>
          <tbody>
            ${disputes.map((d) => `
              <tr>
                <td>
                  <div class="fw-bold small">${esc(d.reporterName)} <span class="text-muted fw-normal">vs</span> ${esc(d.partnerName)}</div>
                  <div class="small text-muted font-monospace">${fmtDate(d.createdAt)}</div>
                </td>
                <td><div class="small fw-medium">${esc(d.reason)}</div></td>
                <td>
                  <span class="badge ${d.status === "OPEN" ? "bg-warning text-dark" : "bg-success"} rounded-pill">
                    ${esc(d.status)}
                  </span>
                </td>
                <td><div class="small text-muted">${esc(d.resolution || "Menunggu keputusan...")}</div></td>
                <td class="text-end">
                  ${d.status === "OPEN" ? `
                    <button class="btn btn-sm btn-primary" data-resolve-dispute="${esc(d.id)}" data-partner="${esc(d.partnerName)}" data-reporter="${esc(d.reporterName)}">
                      <i class="bi bi-hammer me-1"></i>Selesaikan
                    </button>
                  ` : `<span class="small text-success">✓ Selesai</span>`}
                </td>
              </tr>`).join("")}
          </tbody>
        </table>
      </div>`;
  }

  function renderVerificationTab() {
    if (!campusRequests.length) return '<div class="gk-card p-4 text-center text-muted">Tidak ada permintaan verifikasi kampus yang menunggu.</div>';
    return `<div class="gk-card p-3"><div class="small text-muted mb-3">Admin perlu memeriksa bukti identitas kampus sebelum menyetujui permintaan.</div>${campusRequests.map((r) => `<div class="d-flex justify-content-between align-items-center gap-3 border-top py-3"><div><b>${esc(r.fullName)}</b> · ${esc(r.accountEmail)}<div class="small text-muted">Email kampus: ${esc(r.campusEmail)} · Profil kampus: ${esc(r.campus || "-")}</div></div><div class="d-flex gap-2"><button class="btn btn-sm btn-outline-danger" data-review-campus="${esc(r.id)}" data-approved="false">Tolak</button><button class="btn btn-sm btn-success" data-review-campus="${esc(r.id)}" data-approved="true">Verifikasi</button></div></div>`).join("")}</div>`;
  }

  function renderChatReports() {
    if (!chatReports.length) return '<div class="gk-card p-4 text-center text-muted">Tidak ada laporan chat terbuka.</div>';
    return `<div class="table-responsive"><table class="table table-hover align-middle gk-card"><thead><tr><th>Pelapor</th><th>Pengirim pesan</th><th>Alasan</th><th>Waktu</th><th>Tindakan</th></tr></thead><tbody>${chatReports.map(r=>`<tr><td>${esc(r.reporterName)}</td><td>${esc(r.senderName)}</td><td>${esc(r.reason)}</td><td>${fmtDate(r.createdAt)}</td><td><button class="btn btn-sm btn-primary" data-chat-review="${esc(r.id)}">Tandai ditinjau</button> <button class="btn btn-sm btn-outline-secondary" data-chat-dismiss="${esc(r.id)}">Abaikan</button></td></tr>`).join("")}</tbody></table></div>`;
  }
  function renderFeedback() {
    if (!feedback.length) return '<div class="gk-card p-4 text-center text-muted">Belum ada masukan aplikasi.</div>';
    return `<div class="row g-3">${feedback.map(f=>`<article class="col-md-6"><div class="gk-card p-3"><div class="d-flex justify-content-between"><b>${esc(f.fullName)}</b><span class="small text-muted">${fmtDate(f.createdAt)}</span></div><p class="mb-0 mt-2">${esc(f.message)}</p></div></article>`).join("")}</div>`;
  }

  function renderUsersTab() {
    return `
      <div class="gk-card p-3 mb-3">
        <input type="search" id="user-filter-input" class="form-control" placeholder="Cari nama atau email pengguna…">
      </div>
      <div class="table-responsive">
        <table class="table table-hover align-middle gk-card" id="user-table">
          <thead class="table-light">
            <tr>
              <th>Pengguna</th>
              <th>Email</th>
              <th>Peran</th>
              <th>Status</th>
              <th>Strike</th>
              <th class="text-end">Aksi</th>
            </tr>
          </thead>
          <tbody>
            ${users.map((u) => {
      const active = u.status === "ACTIVE";
      return `
                <tr data-user-name="${esc((u.fullName || "").toLowerCase())}" data-user-email="${esc((u.email || "").toLowerCase())}">
                  <td>
                    <div class="fw-bold">${esc(u.fullName)}</div>
                    <a href="#/profile/${esc(u.id)}" class="small text-muted">Lihat profil publik</a>
                  </td>
                  <td><span class="small">${esc(u.email)}</span></td>
                  <td><span class="badge ${u.role === "ADMIN" ? "bg-dark" : "bg-info text-dark"}">${esc(u.role)}</span></td>
                  <td>
                    <span class="badge ${active ? "bg-success" : "bg-danger"} rounded-pill">
                      ${esc(u.status)}
                    </span>
                    ${u.verified ? '<span class="badge bg-info text-dark ms-1">Terverifikasi</span>' : ""}
                  </td>
                  <td>${esc(u.strikeCount ?? 0)}</td>
                  <td class="text-end">
                    ${u.role !== "ADMIN" ? `
                      <button class="btn btn-sm ${active ? "btn-outline-danger" : "btn-outline-success"}"
                              data-toggle-status="${esc(u.id)}" data-next-status="${active ? "SUSPENDED" : "ACTIVE"}">
                        ${active ? '<i class="bi bi-slash-circle me-1"></i>Tangguhkan' : '<i class="bi bi-check-lg me-1"></i>Aktifkan'}
                      </button>` : `<span class="small text-muted">—</span>`}
                  </td>
                </tr>`;
    }).join("")}
          </tbody>
        </table>
      </div>`;
  }

  function renderCategoriesTab() {
    return `
      <div class="row g-4 mb-4">
        <!-- Form Buat Kategori Baru -->
        <div class="col-md-6">
          <div class="gk-card p-4 h-100">
            <h2 class="h6 fw-bold mb-3"><i class="bi bi-folder-plus text-primary me-2"></i>Tambah Kategori Baru</h2>
            <form id="form-add-cat" class="d-flex flex-column gap-3">
              <div>
                <label class="form-label small fw-bold">Nama Kategori</label>
                <input name="name" class="form-control" required placeholder="Contoh: Pemrograman Mobile, Seni Musik…">
              </div>
              <div>
                <label class="form-label small fw-bold">Deskripsi (opsional)</label>
                <textarea name="description" class="form-control" rows="2" placeholder="Penjelasan singkat kategori"></textarea>
              </div>
              <button class="gk-btn text-white" style="background:var(--gk-primary)">Simpan Kategori</button>
            </form>
          </div>
        </div>

        <!-- Form Tambah Keahlian ke Katalog -->
        <div class="col-md-6">
          <div class="gk-card p-4 h-100">
            <h2 class="h6 fw-bold mb-3"><i class="bi bi-plus-circle text-success me-2"></i>Tambah Keahlian ke Katalog</h2>
            <form id="form-add-skill" class="d-flex flex-column gap-3">
              <div>
                <label class="form-label small fw-bold">Pilih Kategori</label>
                <select name="categoryId" class="form-select" required>
                  <option value="">— pilih kategori tujuan —</option>
                  ${categories.map((c) => `<option value="${esc(c.id)}">${esc(c.name)}</option>`).join("")}
                </select>
              </div>
              <div>
                <label class="form-label small fw-bold">Nama Keahlian</label>
                <input name="name" class="form-control" required placeholder="Contoh: Flutter, Docker, Blender 3D…">
              </div>
              <div>
                <label class="form-label small fw-bold">Deskripsi</label>
                <textarea name="description" class="form-control" rows="2" placeholder="Penjelasan keahlian"></textarea>
              </div>
              <button class="gk-btn text-white" style="background:var(--gk-success)">Simpan Keahlian</button>
            </form>
          </div>
        </div>
      </div>

      <!-- Daftar Kategori & Keahlian yang Ada -->
      <h2 class="h6 fw-bold mb-3">Daftar Kategori &amp; Keahlian Terdaftar</h2>
      <div class="row g-3">
        ${categories.map((c) => `
          <div class="col-md-6">
            <div class="gk-card p-3 h-100">
              <div class="d-flex justify-content-between align-items-center mb-2">
                <span class="fw-bold" style="color:var(--gk-primary)">${esc(c.name)}</span>
                <span class="badge bg-secondary rounded-pill">${(c.skills || []).length} keahlian</span>
              </div>
              ${c.description ? `<p class="small text-muted mb-2">${esc(c.description)}</p>` : ""}
              <div class="d-flex flex-wrap gap-1 mt-2">
                ${(c.skills || []).map((s) => `
                  <span class="badge bg-light text-dark border" style="font-size:12px">${esc(typeof s === "string" ? s : s?.name)}</span>
                `).join("")}
              </div>
            </div>
          </div>`).join("")}
      </div>`;
  }

  function attachEvents() {
    const $ = (s) => view.querySelector(s);
    const showMsg = (m, type) => { $("#admin-msg").innerHTML = alertBox(m, type); };

    // Tab switcher
    view.querySelectorAll("button[data-tab]").forEach((btn) => {
      btn.onclick = () => {
        activeTab = btn.dataset.tab;
        render();
      };
    });
    view.querySelectorAll("[data-chat-review],[data-chat-dismiss]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.dataset.chatReview || btn.dataset.chatDismiss;
        try {
          await api(`/api/admin/chat-reports/${id}/review`, {
            method: "POST",
            body: { dismiss: btn.hasAttribute("data-chat-dismiss") },
          });
          toast("Laporan chat ditinjau");
          const idx = chatReports.findIndex((r) => String(r.id) === String(id));
          if (idx !== -1) chatReports.splice(idx, 1);
          render();
        } catch (e) { showMsg(e.message); }
      };
    });

    // Dismiss report
    view.querySelectorAll("button[data-dismiss-report]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.dataset.dismissReport;
        if (!confirm("Abaikan dan tutup laporan ini?")) return;
        try {
          await api(`/api/admin/reports/${id}/review`, {
            method: "POST",
            body: { decision: "DISMISSED", hidePortfolio: false, suspendUser: false },
          });
          toast("Laporan diabaikan");
          const idx = reports.findIndex((r) => r.id === id);
          if (idx !== -1) reports.splice(idx, 1);
          if (summary.openReports) summary.openReports = Math.max(0, summary.openReports - 1);
          render();
        } catch (e) { showMsg(e.message); }
      };
    });

    // Action report (Suspend / Hide)
    view.querySelectorAll("button[data-action-report]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.dataset.actionReport;
        const targetType = btn.dataset.targetType;
        const confirmText = targetType === "USER"
          ? "Tindak laporan dan TANGGUHKAN akun pengguna ini?"
          : "Tindak laporan dan SEMBUNYIKAN portofolio ini?";
        if (!confirm(confirmText)) return;
        try {
          await api(`/api/admin/reports/${id}/review`, {
            method: "POST",
            body: {
              decision: "RESOLVED",
              hidePortfolio: targetType === "PORTFOLIO",
              suspendUser: targetType === "USER",
            },
          });
          toast("Laporan berhasil ditindak");
          const idx = reports.findIndex((r) => r.id === id);
          if (idx !== -1) {
            const target = reports[idx];
            if (targetType === "USER") {
              const u = users.find((x) => x.id === target.targetId);
              if (u) u.status = "SUSPENDED";
            }
            reports.splice(idx, 1);
          }
          if (summary.openReports) summary.openReports = Math.max(0, summary.openReports - 1);
          render();
        } catch (e) { showMsg(e.message); }
      };
    });

    // Toggle user status
    view.querySelectorAll("button[data-toggle-status]").forEach((btn) => {
      btn.onclick = async () => {
        const id = btn.dataset.toggleStatus;
        const nextStatus = btn.dataset.nextStatus;
        if (!confirm(`Ubah status akun pengguna menjadi ${nextStatus}?`)) return;
        try {
          await api(`/api/admin/users/${id}/status`, {
            method: "POST",
            body: { status: nextStatus },
          });
          toast(`Status akun diubah menjadi ${nextStatus}`);
          const u = users.find((x) => x.id === id);
          if (u) u.status = nextStatus;
          render();
        } catch (e) { showMsg(e.message); }
      };
    });

    view.querySelectorAll("button[data-review-campus]").forEach((btn) => btn.onclick = async () => {
      const approved = btn.dataset.approved === "true";
      if (!confirm(approved ? "Setujui verifikasi email kampus? Pastikan identitas sudah diperiksa." : "Tolak permintaan verifikasi ini?")) return;
      try { await api(`/api/admin/campus-verifications/${btn.dataset.reviewCampus}/review`, { method: "POST", body: { approved } }); toast(approved ? "Akun terverifikasi" : "Permintaan ditolak"); const i = campusRequests.findIndex((r) => r.id === btn.dataset.reviewCampus); if (i >= 0) campusRequests.splice(i, 1); render(); }
      catch (e) { showMsg(e.message); }
    });

    // Resolve dispute
    const dlgDisp = view.querySelector("#dlg-resolve-dispute");
    view.querySelectorAll("button[data-resolve-dispute]").forEach((btn) => {
      btn.onclick = () => {
        const id = btn.dataset.resolveDispute;
        const pName = btn.dataset.partner;
        const rName = btn.dataset.reporter;
        dlgDisp.innerHTML = `
          <form method="dialog" id="form-resolve-disp" class="gk-card p-4 d-flex flex-column gap-3">
            <div class="d-flex justify-content-between align-items-start">
              <h2 class="h5 fw-bold m-0"><i class="bi bi-hammer text-primary me-2"></i>Selesaikan Sengketa Barter</h2>
              <button type="button" class="gk-icon-btn" data-close aria-label="Tutup"><i class="bi bi-x-lg"></i></button>
            </div>
            <div id="disp-dlg-msg"></div>
            <div>
              <label class="form-label small fw-bold">Keputusan / Catatan Solusi Admin</label>
              <textarea name="resolution" class="form-control" rows="3" required placeholder="Jelaskan hasil peninjauan dan keputusan admin..."></textarea>
            </div>
            <div class="form-check">
              <input class="form-check-input" type="checkbox" name="addStrike" id="chk-strike">
              <label class="form-check-label small fw-bold text-danger" for="chk-strike">
                Berikan 1 Strike (Poin Pelanggaran) kepada ${esc(pName)}
              </label>
            </div>
            <div class="form-check">
              <input class="form-check-input" type="checkbox" name="removeStrike" id="chk-remove-strike">
              <label class="form-check-label small fw-bold text-success" for="chk-remove-strike">
                Hapus 1 strike dari ${esc(rName)} bila sengketa membuktikan ketidakhadiran tercatat keliru
              </label>
            </div>
            <div class="d-flex justify-content-end gap-2">
              <button type="button" class="gk-btn gk-btn--secondary" data-close>Batal</button>
              <button class="gk-btn gk-btn--primary" type="submit">Konfirmasi Keputusan</button>
            </div>
          </form>`;

        dlgDisp.querySelectorAll("[data-close]").forEach((b) => (b.onclick = () => dlgDisp.close()));
        dlgDisp.querySelector("#form-resolve-disp").onsubmit = async (ev) => {
          ev.preventDefault();
          const fd = new FormData(ev.target);
          try {
            await api(`/api/disputes/admin/${id}/resolve`, {
              method: "POST",
              body: {
                resolution: fd.get("resolution").toString().trim(),
                addStrikeToPartner: dlgDisp.querySelector("#chk-strike").checked,
                removeStrikeFromReporter: dlgDisp.querySelector("#chk-remove-strike").checked
              }
            });
            dlgDisp.close();
            toast("Sengketa berhasil diselesaikan!");
            const dObj = disputes.find((x) => x.id === id);
            if (dObj) {
              dObj.status = "RESOLVED";
              dObj.resolution = fd.get("resolution").toString().trim();
            }
            render();
          } catch (e) {
            dlgDisp.querySelector("#disp-dlg-msg").innerHTML = alertBox(e.message);
          }
        };
        dlgDisp.showModal();
      };
    });

    // User table filter
    const userFilter = $("#user-filter-input");
    if (userFilter) {
      userFilter.oninput = (ev) => {
        const kw = ev.target.value.toLowerCase().trim();
        view.querySelectorAll("#user-table tbody tr").forEach((row) => {
          const match = !kw ||
            row.dataset.userName.includes(kw) ||
            row.dataset.userEmail.includes(kw);
          row.style.display = match ? "" : "none";
        });
      };
    }

    // Add category form
    const catForm = $("#form-add-cat");
    if (catForm) {
      catForm.onsubmit = async (ev) => {
        ev.preventDefault();
        const fd = new FormData(ev.target);
        try {
          const newCat = await api("/api/admin/categories", {
            method: "POST",
            body: { name: fd.get("name"), description: fd.get("description") || "" },
          });
          toast(`Kategori “${newCat.name}” berhasil ditambahkan`);
          categories.push({ ...newCat, skills: [] });
          render();
        } catch (e) { showMsg(e.message); }
      };
    }

    // Add skill form
    const skillForm = $("#form-add-skill");
    if (skillForm) {
      skillForm.onsubmit = async (ev) => {
        ev.preventDefault();
        const fd = new FormData(ev.target);
        const catId = fd.get("categoryId");
        const skillName = fd.get("name");
        try {
          const newSkill = await api("/api/admin/skills", {
            method: "POST",
            body: { categoryId: catId, name: skillName, description: fd.get("description") || "" },
          });
          toast(`Keahlian “${newSkill.name}” berhasil ditambahkan ke katalog`);
          const targetCat = categories.find((c) => c.id === catId);
          if (targetCat) {
            targetCat.skills = targetCat.skills || [];
            targetCat.skills.push(newSkill.name);
          }
          render();
        } catch (e) { showMsg(e.message); }
      };
    }
  }

  render();
}
