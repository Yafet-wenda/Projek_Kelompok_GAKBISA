export const esc = (s) =>
  String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

export const fmtDate = (iso) =>
  iso ? new Date(iso).toLocaleString("id-ID", { dateStyle: "medium", timeStyle: "short" }) : "-";

/** Nilai untuk <input type="datetime-local"> dari ISO (zona waktu lokal). */
export const toLocalInput = (iso) => {
  const d = new Date(iso);
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
};

export function toast(message, kind = "success") {
  const el = document.createElement("div");
  el.className = `gk-alert gk-alert--${kind}`;
  el.style.minWidth = "260px";
  el.textContent = message;
  document.getElementById("toasts").appendChild(el);
  setTimeout(() => el.remove(), 4000);
}

export const alertBox = (msg, kind = "danger") =>
  `<div class="gk-alert gk-alert--${kind}" role="alert">${esc(msg)}</div>`;

export const statusBadge = (s) => `<span class="gk-badge gk-badge--${esc(s)}">${esc(s)}</span>`;
