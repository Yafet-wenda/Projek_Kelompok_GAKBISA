/**
 * gk-navbar.js
 * Tugas: (1) tandai item aktif di navbar + bottom-nav berdasar URL,
 *        (2) tampilkan badge notifikasi unread-count dari API,
 *        (3) toggle mobile search bar.
 *
 * Tidak ada fetch ke route baru — badge pakai endpoint /api/notifications/unread-count
 * yang sudah ada di NotificationController.
 * Semua DOM manipulation murni presentasi; tidak menyentuh state/bisnis.
 */

(function () {
  'use strict';

  /* ── 1. Tandai item aktif berdasarkan window.location.pathname ─────────── */
  function markActive() {
    const path = window.location.pathname;

    // Kumpulkan semua nav-tab (desktop) dan bottom-nav item (mobile)
    const tabs = document.querySelectorAll('.gk-nav-tab[data-path], .gk-bottom-nav__item[data-path]');

    tabs.forEach(function (tab) {
      const tabPath = tab.getAttribute('data-path');
      if (!tabPath) return;

      // Cocokkan dengan prefix agar /barter/123 tetap menandai tab /barter
      const isActive = path === tabPath || path.startsWith(tabPath + '/');
      tab.classList.toggle('active', isActive);

      if (isActive) {
        tab.setAttribute('aria-current', 'page');
      } else {
        tab.removeAttribute('aria-current');
      }
    });
  }

  /* ── 2. Fetch unread notification count ──────────────────────────────────
     Endpoint: GET /api/notifications
     Response JSON: [ { "id": "...", "read": false, ... }, ... ]
     Badge disembunyikan bila count = 0.
  ─────────────────────────────────────────────────────────────────────────── */
  function loadNotifBadge() {
    const badge = document.getElementById('gk-notif-badge');
    if (!badge) return; // belum login / fragment tidak ada

    fetch('/api/notifications', {
      credentials: 'same-origin',
      headers: { 'X-Requested-With': 'XMLHttpRequest' }
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        var count = 0;
        if (Array.isArray(data)) {
          count = data.filter(function (n) { return !n.read; }).length;
        } else if (data && typeof data.count === 'number') {
          count = data.count;
        }
        badge.textContent = count > 99 ? '99+' : String(count);
        badge.hidden = count === 0;
      })
      .catch(function () {
        // Gagal fetch (belum auth, CSRF, dll.) — sembunyikan badge saja
        badge.hidden = true;
      });
  }

  /* ── 3. Mobile search toggle ─────────────────────────────────────────────
     Tombol ikon search di kanan navbar membuka/menutup bar di bawah navbar.
  ─────────────────────────────────────────────────────────────────────────── */
  function initMobileSearch() {
    var toggleBtn = document.getElementById('gk-mobile-search-toggle');
    var closeBtn  = document.getElementById('gk-mobile-search-close');
    var searchBar = document.getElementById('gk-mobile-search');
    var input     = searchBar && searchBar.querySelector('.gk-mobile-search__input');

    if (!toggleBtn || !searchBar) return;

    function openSearch() {
      searchBar.hidden = false;
      if (input) { input.focus(); }
      toggleBtn.setAttribute('aria-expanded', 'true');
    }

    function closeSearch() {
      searchBar.hidden = true;
      toggleBtn.setAttribute('aria-expanded', 'false');
    }

    toggleBtn.addEventListener('click', function () {
      if (searchBar.hidden) { openSearch(); } else { closeSearch(); }
    });

    if (closeBtn) { closeBtn.addEventListener('click', closeSearch); }

    // Tutup saat tekan Escape
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !searchBar.hidden) { closeSearch(); }
    });
  }

  /* ── 4. Bootstrap tooltip init ───────────────────────────────────────────
     Tooltip muncul pada hover .gk-nav-tab (data-bs-toggle="tooltip").
     Diinisialisasi setelah Bootstrap dimuat.
  ─────────────────────────────────────────────────────────────────────────── */
  function initTooltips() {
    if (typeof bootstrap === 'undefined' || !bootstrap.Tooltip) return;
    var els = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    els.forEach(function (el) {
      // Hanya aktifkan tooltip di desktop (pointer: fine)
      if (window.matchMedia('(pointer: fine)').matches) {
        new bootstrap.Tooltip(el, { trigger: 'hover' });
      }
    });
  }

  /* ── Inisialisasi ─────────────────────────────────────────────────────── */
  function init() {
    markActive();
    loadNotifBadge();
    initMobileSearch();
    initTooltips();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

