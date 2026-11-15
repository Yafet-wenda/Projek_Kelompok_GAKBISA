/* ============================================================
   GAKBISA: ruang meeting Jitsi di halaman detail sesi.
   - JaaS aktif    : meeting tampil di dalam halaman (iFrame API + JWT dari backend).
   - JaaS nonaktif : tombol membuka Jitsi publik / tautan eksternal di tab baru.
   Butuh elemen #gk-jitsi-start, #gk-jitsi-msg, dan #gk-jitsi-embed[data-session-id].
   ============================================================ */
(function () {
  "use strict";

  var box = document.getElementById("gk-jitsi-embed");
  var startBtn = document.getElementById("gk-jitsi-start");
  var msg = document.getElementById("gk-jitsi-msg");
  if (!box || !startBtn) return;

  var sessionId = box.dataset.sessionId;
  var api = null;

  function setMsg(text) {
    if (msg) msg.textContent = text || "";
  }

  // Kalau browser memblokir tab baru, tampilkan tautan yang bisa diklik.
  function showLink(url) {
    if (!msg) return;
    msg.textContent = "Tab baru diblokir browser. ";
    var a = document.createElement("a");
    a.href = url;
    a.target = "_blank";
    a.rel = "noopener noreferrer";
    a.className = "text-white fw-bold";
    a.textContent = "Klik di sini untuk membuka meeting";
    msg.appendChild(a);
  }

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      if (window.JitsiMeetExternalAPI) return resolve();
      var s = document.createElement("script");
      s.src = src;
      s.async = true;
      s.onload = resolve;
      s.onerror = function () { reject(new Error("Gagal memuat pustaka Jitsi. Periksa koneksi internet.")); };
      document.head.appendChild(s);
    });
  }

  function mount(m) {
    box.style.display = "block";
    startBtn.style.display = "none";
    api = new window.JitsiMeetExternalAPI(m.domain, {
      roomName: m.roomName,
      jwt: m.jwt,
      parentNode: box,
      width: "100%",
      height: "100%",
      userInfo: { displayName: m.displayName }
    });
    api.addListener("readyToClose", stop);
  }

  function stop() {
    if (api) { api.dispose(); api = null; }
    box.innerHTML = "";
    box.style.display = "none";
    startBtn.style.display = "";
    startBtn.disabled = false;
  }

  function start() {
    setMsg("");
    startBtn.disabled = true;

    fetch("/api/sessions/" + encodeURIComponent(sessionId) + "/meeting", {
      credentials: "same-origin",
      headers: { Accept: "application/json" }
    })
      .then(function (r) {
        return r.json().catch(function () { return {}; }).then(function (data) {
          if (!r.ok) {
            throw new Error(data.detail || data.message || "Tidak bisa membuka ruang meeting (" + r.status + ")");
          }
          return data;
        });
      })
      .then(function (m) {
        if (!m.embedded) {
          var w = window.open(m.joinUrl, "_blank", "noopener");
          if (!w) showLink(m.joinUrl);
          startBtn.disabled = false;
          return;
        }
        return loadScript(m.scriptUrl).then(function () { mount(m); });
      })
      .catch(function (e) {
        setMsg(e.message);
        startBtn.disabled = false;
      });
  }

  startBtn.addEventListener("click", start);
})();
