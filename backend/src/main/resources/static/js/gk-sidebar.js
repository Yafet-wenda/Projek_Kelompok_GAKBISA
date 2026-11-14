/**
 * gk-sidebar.js
 * Tugas:
 * 1. Tandai item aktif di sidebar berdasarkan window.location.pathname
 * 2. Handle buka/tutup drawer sidebar di mobile & tablet (<=992px)
 * 3. Tutup drawer saat klik backdrop, tombol close, atau tombol Escape
 */

(function () {
  'use strict';

  function markActiveSidebar() {
    const path = window.location.pathname;
    const items = document.querySelectorAll('.gk-sidebar-item[data-path]');

    items.forEach(function (item) {
      const itemPath = item.getAttribute('data-path');
      if (!itemPath) return;

      // Exact match or prefix match for subpaths (e.g. /barter/123 -> /barter)
      const isActive = path === itemPath || (itemPath !== '/' && path.startsWith(itemPath + '/'));
      item.classList.toggle('active', isActive);

      if (isActive) {
        item.setAttribute('aria-current', 'page');
      } else {
        item.removeAttribute('aria-current');
      }
    });
  }

  function initSidebarDrawer() {
    const sidebar = document.getElementById('gkSidebar');
    const backdrop = document.getElementById('gkSidebarBackdrop');
    const toggleBtn = document.getElementById('gk-sidebar-toggle');
    const closeBtn = document.getElementById('gkSidebarClose');

    if (!sidebar) return;

    function openDrawer() {
      sidebar.classList.add('is-open');
      if (backdrop) backdrop.classList.add('is-open');
      document.body.style.overflow = 'hidden';
      if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'true');
    }

    function closeDrawer() {
      sidebar.classList.remove('is-open');
      if (backdrop) backdrop.classList.remove('is-open');
      document.body.style.overflow = '';
      if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
    }

    if (toggleBtn) {
      toggleBtn.addEventListener('click', function (e) {
        e.preventDefault();
        if (sidebar.classList.contains('is-open')) {
          closeDrawer();
        } else {
          openDrawer();
        }
      });
    }

    if (closeBtn) {
      closeBtn.addEventListener('click', function (e) {
        e.preventDefault();
        closeDrawer();
      });
    }

    if (backdrop) {
      backdrop.addEventListener('click', closeDrawer);
    }

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && sidebar.classList.contains('is-open')) {
        closeDrawer();
      }
    });

    // Close drawer when screen is resized to desktop (>992px)
    window.addEventListener('resize', function () {
      if (window.innerWidth > 992 && sidebar.classList.contains('is-open')) {
        closeDrawer();
      }
    });
  }

  function init() {
    markActiveSidebar();
    initSidebarDrawer();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

