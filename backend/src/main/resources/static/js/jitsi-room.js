/* Akses pertemuan selalu melalui sesi web; JWT JaaS hanya untuk iframe. */
(function () {
  'use strict';

  var box = document.getElementById('gk-jitsi-embed');
  var startBtn = document.getElementById('gk-jitsi-start');
  var msg = document.getElementById('gk-jitsi-msg');
  if (!box || !startBtn || !msg) return;

  var api = null;
  var scriptPromise;

  function safeUrl(value, httpsOnly) {
    var url = new URL(value);
    if ((url.protocol !== 'https:' && (httpsOnly || url.protocol !== 'http:')) || url.username || url.password) {
      throw new Error('Tautan pertemuan tidak valid.');
    }
    return url;
  }

  function showLink(url) {
    msg.textContent = 'Pertemuan siap. ';
    var link = document.createElement('a');
    link.href = url;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.className = 'text-white fw-bold';
    link.textContent = 'Buka pertemuan';
    msg.appendChild(link);
  }

  function loadScript(src) {
    if (window.JitsiMeetExternalAPI) return Promise.resolve();
    if (!scriptPromise) {
      scriptPromise = new Promise(function (resolve, reject) {
        var script = document.createElement('script');
        script.src = src;
        script.async = true;
        script.onload = resolve;
        script.onerror = function () {
          script.remove();
          scriptPromise = null;
          reject(new Error('Pustaka pertemuan gagal dimuat. Coba lagi.'));
        };
        document.head.appendChild(script);
      });
    }
    return scriptPromise;
  }

  function stop() {
    if (api) { api.dispose(); api = null; }
    box.replaceChildren();
    box.style.display = 'none';
    startBtn.hidden = false;
    startBtn.disabled = false;
  }

  async function start() {
    msg.textContent = 'Menyiapkan pertemuan…';
    startBtn.disabled = true;
    try {
      var response = await fetch('/web/api/sessions/' + encodeURIComponent(box.dataset.sessionId) + '/meeting', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' }
      });
      if (response.redirected || response.status === 401) {
        throw new Error('Sesi masuk berakhir. Silakan masuk lagi.');
      }
      var meeting = await response.json();
      if (!response.ok) throw new Error(meeting.detail || meeting.message || 'Pertemuan belum tersedia.');
      if (!meeting.embedded) {
        // Backend validates provider, membership and the URL before returning access.
        showLink(safeUrl(meeting.joinUrl, false).href);
        startBtn.disabled = false;
        return;
      }
      var scriptUrl = safeUrl(meeting.scriptUrl, true);
      if (meeting.domain !== '8x8.vc' || scriptUrl.host !== '8x8.vc' ||
          !scriptUrl.pathname.endsWith('/external_api.js') || !meeting.jwt || !meeting.roomName) {
        throw new Error('Konfigurasi pertemuan tidak valid.');
      }
      await loadScript(scriptUrl.href);
      box.style.display = 'block';
      api = new window.JitsiMeetExternalAPI(meeting.domain, {
        roomName: meeting.roomName,
        jwt: meeting.jwt,
        parentNode: box,
        width: '100%',
        height: '100%',
        userInfo: { displayName: meeting.displayName }
      });
      api.addListener('readyToClose', stop);
      startBtn.hidden = true;
      msg.textContent = '';
    } catch (error) {
      stop();
      msg.textContent = error instanceof SyntaxError ? 'Respons pertemuan tidak valid. Coba lagi.' : error.message;
    }
  }

  startBtn.addEventListener('click', start);
  window.addEventListener('pagehide', stop);
})();
