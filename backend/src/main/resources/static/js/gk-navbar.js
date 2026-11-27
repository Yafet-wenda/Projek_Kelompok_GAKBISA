/**
 * gk-navbar.js
 * Tugas: (1) tandai tautan aktif di bilah atas dan navigasi bawah,
 *        (2) buka/tutup panel menu (notifikasi dan akun),
 *        (3) tampilkan jumlah notifikasi belum dibaca,
 *        (4) toggle bilah pencarian di layar sempit.
 *
 * Badge dibaca melalui sesi web di /web/api/notifications.
 */

(function () {
  'use strict';

  function markActive() {
    const path = window.location.pathname;
    const links = document.querySelectorAll('.nav-links a[data-path], .bottom-nav a[data-path]');

    links.forEach(function (link) {
      const target = link.getAttribute('data-path');
      if (!target) return;

      const isActive = path === target || path.startsWith(target + '/');
      link.classList.toggle('active', isActive);

      if (isActive) {
        link.setAttribute('aria-current', 'page');
      } else {
        link.removeAttribute('aria-current');
      }
    });
  }

  function initMenus() {
    const toggles = document.querySelectorAll('[data-menu-toggle]');

    function closeAll(except) {
      document.querySelectorAll('.menu.is-open').forEach(function (menu) {
        if (menu === except) return;
        menu.classList.remove('is-open');
        const btn = menu.querySelector('[data-menu-toggle]');
        if (btn) btn.setAttribute('aria-expanded', 'false');
      });
    }

    toggles.forEach(function (btn) {
      btn.addEventListener('click', function (event) {
        event.stopPropagation();
        const menu = btn.closest('.menu');
        if (!menu) return;

        const willOpen = !menu.classList.contains('is-open');
        closeAll(menu);
        menu.classList.toggle('is-open', willOpen);
        btn.setAttribute('aria-expanded', String(willOpen));
      });
    });

    document.addEventListener('click', function () {
      closeAll(null);
    });

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') closeAll(null);
    });
  }

  function loadNotifBadge() {
    const badge = document.getElementById('gk-notif-badge');
    if (!badge) return;

    fetch('/web/api/notifications', {
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
        badge.hidden = true;
      });
  }

  function initMobileSearch() {
    var toggleBtn = document.getElementById('gk-mobile-search-toggle');
    var closeBtn = document.getElementById('gk-mobile-search-close');
    var searchBar = document.getElementById('gk-mobile-search');
    var input = searchBar && searchBar.querySelector('.gk-mobile-search__input');

    if (!toggleBtn || !searchBar) return;

    function openSearch() {
      searchBar.hidden = false;
      if (input) input.focus();
      toggleBtn.setAttribute('aria-expanded', 'true');
    }

    function closeSearch() {
      searchBar.hidden = true;
      toggleBtn.setAttribute('aria-expanded', 'false');
    }

    toggleBtn.addEventListener('click', function () {
      if (searchBar.hidden) {
        openSearch();
      } else {
        closeSearch();
      }
    });

    if (closeBtn) closeBtn.addEventListener('click', closeSearch);

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && !searchBar.hidden) closeSearch();
    });
  }

  function init() {
    markActive();
    initMenus();
    loadNotifBadge();
    initMobileSearch();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
