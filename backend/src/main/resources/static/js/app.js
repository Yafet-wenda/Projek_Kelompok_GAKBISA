// GAKBISA App JavaScript - Facebook-style UI
document.addEventListener('DOMContentLoaded', function () {
  document.querySelectorAll('time[data-local-time]').forEach(function (time) {
    time.textContent = new Intl.DateTimeFormat('id-ID', {
      dateStyle: 'long', timeStyle: 'short'
    }).format(new Date(time.dateTime));
    time.title = Intl.DateTimeFormat().resolvedOptions().timeZone;
  });

  // Auto-dismiss alerts after 5 seconds
  document.querySelectorAll('.fb-alert').forEach(function (alert) {
    setTimeout(function () {
      alert.style.transition = 'opacity 0.4s';
      alert.style.opacity = '0';
      setTimeout(function () { alert.remove(); }, 400);
    }, 5000);
  });

  // Active nav link highlight
  var currentPath = window.location.pathname;
  document.querySelectorAll('.fb-nav-link, .fb-navbar .nav-tab').forEach(function (link) {
    var href = link.getAttribute('href');
    if (href && currentPath.startsWith(href) && href !== '/') {
      link.classList.add('active');
    }
    if (href === '/' && currentPath === '/') {
      link.classList.add('active');
    }
  });

  // Search bar focus effect
  var searchInput = document.querySelector('.fb-navbar .search-wrapper input');
  if (searchInput) {
    searchInput.addEventListener('focus', function () {
      this.closest('.search-wrapper').style.background = '#fff';
      this.closest('.search-wrapper').style.boxShadow = '0 0 0 2px var(--gk-primary)';
    });
    searchInput.addEventListener('blur', function () {
      this.closest('.search-wrapper').style.background = '';
      this.closest('.search-wrapper').style.boxShadow = '';
    });
  }

  // Skill category → skill filter
  var catSelect = document.getElementById('categorySelect');
  var skillSelect = document.getElementById('skillSelect');
  if (catSelect && skillSelect) {
    catSelect.addEventListener('change', function () {
      var catId = this.value;
      skillSelect.querySelectorAll('option').forEach(function (opt) {
        var excluded = Boolean(opt.value && catId && opt.dataset.cat !== catId);
        opt.hidden = excluded;
        opt.disabled = excluded;
      });
      skillSelect.value = '';
    });
  }

  // Star rating visual
  document.querySelectorAll('.star-rating').forEach(function (container) {
    var stars = container.querySelectorAll('label');
    stars.forEach(function (star, idx) {
      star.addEventListener('mouseover', function () {
        stars.forEach(function (s, i) { s.style.color = i <= idx ? '#f7981c' : ''; });
      });
      star.addEventListener('mouseleave', function () {
        stars.forEach(function (s) { s.style.color = ''; });
      });
    });
  });

  // One preview handler; filenames are always text, never HTML.
  var fileInput = document.getElementById('portfolioFile');
  var preview = document.getElementById('filePreview');
  if (fileInput && preview) {
    var previewUrl;
    fileInput.addEventListener('change', function () {
      if (previewUrl) { URL.revokeObjectURL(previewUrl); previewUrl = null; }
      preview.replaceChildren();
      this.setCustomValidity('');
      var file = this.files[0];
      if (!file) return;
      if (file.size > 50 * 1024 * 1024) {
        this.setCustomValidity('Ukuran berkas maksimal 50 MiB.');
        preview.textContent = this.validationMessage;
        this.reportValidity();
        return;
      }
      var label = document.createElement('p');
      label.className = 'small text-muted';
      label.textContent = file.name + ' · ' + (file.size / 1024 / 1024).toFixed(2) + ' MiB';
      preview.appendChild(label);
      var image = /^image\/(jpeg|png|webp)$/.test(file.type);
      var video = /^video\/(mp4|webm)$/.test(file.type);
      if (image || video) {
        var media = document.createElement(image ? 'img' : 'video');
        previewUrl = URL.createObjectURL(file);
        media.src = previewUrl;
        media.className = 'img-fluid rounded';
        media.style.maxHeight = '220px';
        if (image) media.alt = 'Pratinjau berkas';
        else { media.controls = true; media.preload = 'metadata'; }
        preview.appendChild(media);
      }
    });
    window.addEventListener('pagehide', function () {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    });
  }

  // Confirm before delete/cancel actions
  document.querySelectorAll('[data-confirm]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      if (!confirm(this.dataset.confirm)) {
        e.preventDefault();
        return false;
      }
    });
  });

  // Smooth scroll for anchor links
  document.querySelectorAll('a[href^="#"]').forEach(function (anchor) {
    anchor.addEventListener('click', function (e) {
      var target = document.getElementById(this.getAttribute('href').slice(1));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth' });
      }
    });
  });

  // Native form submissions use local time and its offset for the chosen date.
  document.querySelectorAll('input[type="datetime-local"][data-instant]').forEach(function (input) {
    var date = new Date(input.dataset.instant);
    if (isNaN(date.getTime())) return;
    var local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    input.value = local.toISOString().slice(0, 16);
  });
  document.querySelectorAll('input[name="timezoneOffset"]').forEach(function (offset) {
    var form = offset.form;
    var scheduled = form.querySelector('[name="scheduledAt"]');
    function updateOffset() {
      var date = scheduled && scheduled.value ? new Date(scheduled.value) : new Date();
      offset.value = String(isNaN(date.getTime()) ? new Date().getTimezoneOffset() : date.getTimezoneOffset());
    }
    updateOffset();
    if (scheduled) scheduled.addEventListener('change', updateOffset);
    form.addEventListener('submit', updateOffset);
  });
  document.querySelectorAll('select[name="meetingProvider"]').forEach(function (select) {
    var field = select.form.querySelector('[name="meetingUrl"]');
    var group = select.form.querySelector('[data-external-meeting]');
    if (!field || !group) return;
    function updateProvider() {
      var external = select.value === 'EXTERNAL';
      group.hidden = !external;
      field.disabled = !external;
      field.required = external;
    }
    updateProvider();
    select.addEventListener('change', updateProvider);
  });

  document.querySelectorAll('[data-dialog-open]').forEach(function (button) {
    button.addEventListener('click', function () {
      var dialog = document.getElementById(button.dataset.dialogOpen);
      if (dialog) dialog.showModal();
    });
  });
  document.querySelectorAll('[data-dialog-close]').forEach(function (button) {
    button.addEventListener('click', function () { button.closest('dialog').close(); });
  });
});
/* ============================================================
   GAKBISA: util bersama untuk halaman berbasis fetch.
   Diekspos sebagai window.Gakbisa.
   ============================================================ */
(function () {
  "use strict";

  const meta = (n) => document.querySelector(`meta[name="${n}"]`)?.content || "";
  const TOKEN = meta("_csrf");
  const HEADER = meta("_csrf_header") || "X-CSRF-TOKEN";

  /* ---------- pengaman XSS: template literal yang meng-escape ---------- */
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // Hasil html() dibungkus String object bercap __raw: aman di-escape ulang saat
  // disisipkan ke template lain, tapi tetap tersusun jadi string biasa saat dipakai
  // langsung (innerHTML, join, atau konkatenasi).
  const raw = (v) => Object.assign(new String(v), { __raw: true });

  function html(strings, ...vals) {
    let out = strings[0];
    vals.forEach((v, i) => {
      out += render(v) + strings[i + 1];
    });
    return raw(out);
  }
  function render(v) {
    if (v == null || v === false) return "";
    if (Array.isArray(v)) return v.map(render).join("");
    if (typeof v === "object" && v.__raw) return v.toString();
    return esc(v);
  }

  /* ---------- HTTP ---------- */
  class ApiError extends Error {
    constructor(message, status) {
      super(message);
      this.status = status;
    }
  }

  async function request(path, options = {}) {
    const opt = Object.assign({ headers: {} }, options);
    opt.headers = Object.assign({}, opt.headers);
    if (opt.body !== undefined && !(opt.body instanceof FormData) && !opt.headers["Content-Type"]) {
      opt.headers["Content-Type"] = "application/json";
      if (typeof opt.body !== "string") opt.body = JSON.stringify(opt.body);
    }
    if (opt.method && opt.method !== "GET" && TOKEN) opt.headers[HEADER] = TOKEN;
    opt.credentials = "same-origin";

    let res;
    try {
      res = await fetch(path, opt);
    } catch (e) {
      throw new ApiError("Koneksi ke server gagal. Pastikan aplikasi masih berjalan.", 0);
    }
    if (res.redirected && res.url.includes("/login")) {
      throw new ApiError("Sesi Anda berakhir. Silakan masuk lagi.", 401);
    }
    const text = await res.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch { data = { message: text }; }
    }
    if (!res.ok) {
      const msg = (data && (data.message || data.error)) || `Permintaan gagal (HTTP ${res.status}).`;
      throw new ApiError(msg, res.status);
    }
    return data;
  }

  const api = {
    get: (p) => request(p),
    post: (p, body) => request(p, { method: "POST", body: body === undefined ? {} : body }),
    patch: (p, body) => request(p, { method: "PATCH", body: body }),
    del: (p) => request(p, { method: "DELETE" }),
    postForm: (p, formData) => request(p, { method: "POST", body: formData }),
    ApiError,
  };

  /* ---------- tampilan ---------- */
  let toastBox;
  function toast(message, kind = "ok", ms = 4200) {
    if (!toastBox) {
      toastBox = document.createElement("div");
      toastBox.className = "toasts";
      toastBox.setAttribute("aria-live", "polite");
      document.body.appendChild(toastBox);
    }
    const t = document.createElement("div");
    t.className = `toast toast--${kind}`;
    t.textContent = message;
    toastBox.appendChild(t);
    setTimeout(() => {
      t.style.transition = "opacity .3s ease, transform .3s ease";
      t.style.opacity = "0";
      t.style.transform = "translateY(8px)";
      setTimeout(() => t.remove(), 320);
    }, ms);
  }

  const STATUS_LABEL = {
    PENDING: "Menunggu", ACCEPTED: "Diterima", REJECTED: "Ditolak",
    CANCELLED: "Dibatalkan", COMPLETED: "Selesai", SCHEDULED: "Terjadwal",
    ONGOING: "Berlangsung", PUBLIC: "Publik", PRIVATE: "Privat",
  };
  const statusTag = (s) =>
    html`<span class="status" data-s="${s || ""}">${STATUS_LABEL[s] || s}</span>`;

  const LEVEL_LABEL = { BEGINNER: "Pemula", INTERMEDIATE: "Menengah", ADVANCED: "Mahir" };

  function tanggal(iso, opts) {
    if (!iso) return "—";
    const d = new Date(iso);
    if (isNaN(d)) return String(iso);
    return d.toLocaleDateString("id-ID", opts || { day: "numeric", month: "short", year: "numeric" });
  }
  function jam(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    if (isNaN(d)) return "";
    return d.toLocaleTimeString("id-ID", { hour: "2-digit", minute: "2-digit" });
  }
  function relatif(iso) {
    if (!iso) return "";
    const detik = (Date.now() - new Date(iso).getTime()) / 1000;
    if (detik < 60) return "baru saja";
    if (detik < 3600) return `${Math.floor(detik / 60)} menit lalu`;
    if (detik < 86400) return `${Math.floor(detik / 3600)} jam lalu`;
    if (detik < 604800) return `${Math.floor(detik / 86400)} hari lalu`;
    return tanggal(iso);
  }
  function ukuran(byte) {
    if (!byte && byte !== 0) return "—";
    if (byte < 1024) return `${byte} B`;
    if (byte < 1048576) return `${(byte / 1024).toFixed(0)} KB`;
    return `${(byte / 1048576).toFixed(1)} MB`;
  }

  /* ---------- pembantu DOM ---------- */
  function on(selector, event, fn) {
    document.querySelectorAll(selector).forEach((el) => el.addEventListener(event, fn));
  }
  function delegate(root, event, selector, fn) {
    root.addEventListener(event, (e) => {
      const el = e.target.closest(selector);
      if (el && root.contains(el)) fn(e, el);
    });
  }
  function busy(btn, state) {
    if (!btn) return;
    if (state) {
      btn.dataset.label = btn.textContent;
      btn.textContent = "Memproses…";
      btn.disabled = true;
      btn.classList.add("is-busy");
    } else {
      btn.textContent = btn.dataset.label || btn.textContent;
      btn.disabled = false;
      btn.classList.remove("is-busy");
    }
  }

  /* Nilai input datetime-local -> Instant ISO (UTC) yang diterima backend. */
  function keInstant(nilaiInput) {
    if (!nilaiInput) return null;
    const d = new Date(nilaiInput);
    return isNaN(d) ? null : d.toISOString();
  }
  function nilaiInputUntukSekarang(menit = 10) {
    const d = new Date(Date.now() + menit * 60000);
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  window.Gakbisa = {
    api, html, raw, esc, toast, statusTag, LEVEL_LABEL, STATUS_LABEL,
    tanggal, jam, relatif, ukuran, on, delegate, busy, keInstant, nilaiInputUntukSekarang,
  };
})();
