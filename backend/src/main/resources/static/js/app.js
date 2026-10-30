// GAKBISA App JavaScript - Facebook-style UI
document.addEventListener('DOMContentLoaded', function () {

  // Auto-dismiss alerts after 5 seconds
  document.querySelectorAll('.fb-alert').forEach(function (alert) {
    setTimeout(function () {
      alert.style.transition = 'opacity 0.4s';
      alert.style.opacity = '0';
      setTimeout(function () { alert.remove(); }, 400);
    }, 5000);
  });

  // Bootstrap tooltip init
  if (typeof bootstrap !== 'undefined') {
    var tooltipEls = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    tooltipEls.forEach(function (el) { new bootstrap.Tooltip(el); });
  }

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
      this.closest('.search-wrapper').style.boxShadow = '0 0 0 2px #1877F2';
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
        if (!opt.value) { opt.style.display = ''; return; }
        opt.style.display = (catId === '' || opt.dataset.cat === catId) ? '' : 'none';
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

  // File upload preview
  var fileInput = document.getElementById('portfolioFile');
  var preview = document.getElementById('filePreview');
  if (fileInput && preview) {
    fileInput.addEventListener('change', function () {
      var file = this.files[0];
      if (!file) return;
      if (file.type.startsWith('image/')) {
        var reader = new FileReader();
        reader.onload = function (e) {
          preview.innerHTML = '<img src="' + e.target.result + '" class="img-fluid rounded" style="max-height:200px;">';
        };
        reader.readAsDataURL(file);
      } else {
        preview.innerHTML = '<div class="d-flex align-items-center gap-2 p-3 bg-light rounded"><i class="bi bi-file-pdf fs-2 text-danger"></i><span>' + file.name + '</span></div>';
      }
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
      var target = document.querySelector(this.getAttribute('href'));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth' });
      }
    });
  });

  // Jitsi Meet integration
  var jitsiContainer = document.getElementById('jitsi-meet-container');
  if (jitsiContainer) {
    var roomName = jitsiContainer.dataset.room;
    var displayName = jitsiContainer.dataset.name || 'GAKBISA User';
    if (roomName && typeof JitsiMeetExternalAPI !== 'undefined') {
      var api = new JitsiMeetExternalAPI('meet.jit.si', {
        roomName: roomName,
        parentNode: jitsiContainer,
        userInfo: { displayName: displayName },
        configOverwrite: { startWithAudioMuted: false, startWithVideoMuted: false },
        interfaceConfigOverwrite: { TOOLBAR_BUTTONS: ['microphone','camera','chat','desktop','fullscreen','hangup'] }
      });
    }
  }
});
