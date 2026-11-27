/**
 * gk-sidebar.js
 * Tugas: tandai tautan rel yang aktif dan kendalikan rel sebagai laci
 * di layar sempit (<=1024px).
 */

(function () {
  'use strict';

  function markActive() {
    const path = window.location.pathname;
    const items = document.querySelectorAll('.rail-link[data-path]');

    let best = null;
    items.forEach(function (item) {
      const target = item.getAttribute('data-path');
      if (!target) return;
      const matches = path === target || path.startsWith(target + '/');
      if (matches && (best === null || target.length > best.length)) best = target;
    });

    items.forEach(function (item) {
      const isActive = item.getAttribute('data-path') === best;
      item.classList.toggle('active', isActive);

      if (isActive) {
        item.setAttribute('aria-current', 'page');
      } else {
        item.removeAttribute('aria-current');
      }
    });
  }

  function initDrawer() {
    const rail = document.getElementById('gkSidebar');
    const backdrop = document.getElementById('gkSidebarBackdrop');
    const toggleBtn = document.getElementById('gk-sidebar-toggle');
    const closeBtn = document.getElementById('gkSidebarClose');

    if (!rail) {
      if (toggleBtn) toggleBtn.hidden = true;
      return;
    }

    function openDrawer() {
      rail.classList.add('is-open');
      if (backdrop) backdrop.classList.add('is-open');
      document.body.classList.add('no-scroll');
      if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'true');
    }

    function closeDrawer() {
      rail.classList.remove('is-open');
      if (backdrop) backdrop.classList.remove('is-open');
      document.body.classList.remove('no-scroll');
      if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
    }

    if (toggleBtn) {
      toggleBtn.addEventListener('click', function (event) {
        event.preventDefault();
        if (rail.classList.contains('is-open')) {
          closeDrawer();
        } else {
          openDrawer();
        }
      });
    }

    if (closeBtn) {
      closeBtn.addEventListener('click', function (event) {
        event.preventDefault();
        closeDrawer();
      });
    }

    if (backdrop) backdrop.addEventListener('click', closeDrawer);

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape' && rail.classList.contains('is-open')) closeDrawer();
    });

    window.addEventListener('resize', function () {
      if (window.innerWidth > 1024 && rail.classList.contains('is-open')) closeDrawer();
    });
  }

  function init() {
    markActive();
    initDrawer();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
