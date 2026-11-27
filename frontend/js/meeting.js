import { api } from "./api.js";
import { esc, alertBox } from "./ui.js";

const scriptPromises = new Map();

/** Memuat Jitsi external_api.js secara lazy dan hanya sekali per URL. */
function loadScript(url) {
  if (!scriptPromises.has(url)) {
    scriptPromises.set(
      url,
      new Promise((resolve, reject) => {
        if (window.JitsiMeetExternalAPI) return resolve();
        const el = document.createElement("script");
        el.src = url;
        el.async = true;
        el.onload = () => resolve();
        el.onerror = () => {
          scriptPromises.delete(url);
          reject(new Error("Gagal memuat script Jitsi IFrame API"));
        };
        document.head.appendChild(el);
      })
    );
  }
  return scriptPromises.get(url);
}

/**
 * Mengambil akses meeting terotorisasi dari backend lalu menanamkan Jitsi IFrame API
 * jika JaaS aktif, atau membuka tautan eksternal/Jitsi publik sebagai cadangan.
 */
async function mountAccess(container, access) {
  let jitsiApi = null;
  const dispose = () => {
    try { jitsiApi?.dispose?.(); } catch { /* abaikan saat cleanup */ }
    jitsiApi = null;
  };

  if (access.embedded && access.scriptUrl) {
    container.innerHTML = `
      <div class="gk-card p-3 mt-3">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <span class="small fw-bold"><i class="bi bi-camera-video-fill text-primary me-1"></i>Ruang Meeting Jitsi</span>
          <button type="button" class="gk-btn gk-btn--secondary gk-btn--sm" data-close-meeting>Tutup ruang</button>
        </div>
        <div class="gk-jitsi-frame rounded-3 overflow-hidden" style="min-height:500px;background:#0b1220"></div>
      </div>`;
    const frame = container.querySelector(".gk-jitsi-frame");
    container.querySelector("[data-close-meeting]").onclick = () => {
      dispose();
      container.innerHTML = "";
    };
    await loadScript(access.scriptUrl);
    if (!window.JitsiMeetExternalAPI) throw new Error("Jitsi IFrame API tidak tersedia di browser");
    jitsiApi = new window.JitsiMeetExternalAPI(access.domain, {
      roomName: access.roomName,
      jwt: access.jwt,
      parentNode: frame,
      width: "100%",
      height: 500,
      userInfo: { displayName: access.displayName || "Mahasiswa" },
    });
    jitsiApi.addEventListener?.("readyToClose", () => {
      dispose();
      container.innerHTML = "";
    });
    window.addEventListener("hashchange", dispose, { once: true });
    return dispose;
  }

  if (!access.joinUrl) {
    throw new Error("Tautan ruang meeting tidak tersedia");
  }
  window.open(access.joinUrl, "_blank", "noopener,noreferrer");
  container.innerHTML = `
    <div class="gk-card p-3 mt-3 d-flex justify-content-between align-items-center flex-wrap gap-2">
      <div class="small">Ruang meeting dibuka di tab baru. Jika jendela terblokir pop-up, gunakan tautan langsung ini:</div>
      <a class="gk-btn gk-btn--sm text-white" style="background:var(--gk-primary)" href="${esc(access.joinUrl)}" target="_blank" rel="noopener noreferrer">
        <i class="bi bi-box-arrow-up-right me-1"></i>Buka tautan meeting
      </a>
    </div>`;
  return dispose;
}

/** Memasang meeting langsung ke kontainer di halaman detail sesi. */
export async function launchInlineMeeting(sessionId, container, errEl) {
  try {
    const access = await api(`/api/sessions/${sessionId}/meeting`);
    if (errEl) errEl.innerHTML = "";
    return await mountAccess(container, access);
  } catch (e) {
    if (errEl) errEl.innerHTML = alertBox(e.message || "Gagal membuka ruang meeting");
    return null;
  }
}

/** Membuka meeting dari daftar kelompok belajar (dialog untuk JaaS, tab baru + fallback link jika publik). */
export async function launchGroupMeeting(view, sessionId, errEl) {
  try {
    const access = await api(`/api/sessions/${sessionId}/meeting`);
    if (errEl) errEl.innerHTML = "";
    if (access.embedded) {
      const dlg = document.createElement("dialog");
      dlg.className = "gk-dialog";
      dlg.innerHTML = `<section class="gk-card p-3" style="width:min(920px,95vw)"><div id="gm-host"></div></section>`;
      view.append(dlg);
      dlg.showModal();
      const dispose = await mountAccess(dlg.querySelector("#gm-host"), access);
      dlg.querySelector("[data-close-meeting]").onclick = () => dlg.close();
      dlg.addEventListener("close", () => { dispose?.(); dlg.remove(); }, { once: true });
      return;
    }
    if (!access.joinUrl) throw new Error("Tautan ruang meeting tidak tersedia");
    window.open(access.joinUrl, "_blank", "noopener,noreferrer");
  } catch (e) {
    if (errEl) errEl.innerHTML = alertBox(e.message || "Gagal membuka ruang meeting");
  }
}
