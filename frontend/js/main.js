import { api, token } from "./api.js";
import { loadMe, currentUser, isAdmin, logout } from "./auth.js";
import { route, match, navigate, currentPath, currentQuery } from "./router.js";
import { esc, alertBox } from "./ui.js";

import login from "./pages/login.js";
import register from "./pages/register.js";
import dashboard from "./pages/dashboard.js";
import barter from "./pages/barter.js";
import sessions from "./pages/sessions.js";
import sessionDetail from "./pages/session-detail.js";
import notifications from "./pages/notifications.js";
import profile from "./pages/profile.js";
import matching from "./pages/matching.js";
import { skeletonFor } from "./skeleton.js";

// ---- Tabel rute. Halaman baru = satu berkas di js/pages/ + satu baris di sini. ----
route("/login", login, { auth: false, shell: false });
route("/register", register, { auth: false, shell: false });
route("/dashboard", dashboard);
route("/barter", barter);
route("/sessions", sessions);
route("/sessions/:id", sessionDetail);
route("/notifications", notifications);
route("/profile", profile);
route("/matching", matching);

// ---- Tema terang/gelap (disimpan di localStorage, dasar pilihan diset di <head> index.html) ----
function currentTheme() {
  return document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
}
function toggleTheme() {
  const next = currentTheme() === "dark" ? "light" : "dark";
  document.documentElement.setAttribute("data-theme", next);
  document.documentElement.setAttribute("data-bs-theme", next);
  try { localStorage.setItem("gk-theme", next); } catch { /* mode privat: abaikan */ }
  const icon = document.querySelector("#gk-theme-toggle i");
  if (icon) icon.className = "bi " + (next === "dark" ? "bi-sun-fill" : "bi-moon-stars-fill");
}

const app = document.getElementById("app");
let badgeTimer = null;

const NAV = [
  ["/dashboard", "bi-house", "Beranda"],
  ["/barter", "bi-arrow-left-right", "Barter"],
  ["/sessions", "bi-calendar-event", "Sesi"],
  ["/notifications", "bi-bell", "Notifikasi"],
  ["/profile", "bi-person", "Profil"],
];

function shellHtml(path) {
  const links = NAV.map(([href, icon, label]) => {
    const active = path === href || (href !== "/dashboard" && path.startsWith(href + "/"));
    const badge = href === "/notifications" ? ' <span id="gk-notif-badge" class="badge text-bg-danger" hidden>0</span>' : "";
    return `<a href="#${href}" class="nav-link${active ? " active fw-bold" : ""}"><i class="bi ${icon} me-1"></i>${label}${badge}</a>`;
  }).join("");
  return `
    <nav class="navbar navbar-expand border-bottom px-3 py-2" style="background:var(--gk-card-bg)">
      <a class="gk-navbar__brand me-3" href="#/dashboard" aria-label="GAKBISA - beranda">
        <span class="gk-navbar__brand-mark" aria-hidden="true">G</span>
        <span class="gk-navbar__brand-name">GAKBISA</span>
      </a>
      <form id="gk-nav-search" class="gk-navbar__search d-none d-md-flex me-3" role="search">
        <i class="bi bi-search gk-navbar__search-icon" aria-hidden="true"></i>
        <input name="keyword" type="search" class="gk-navbar__search-input" placeholder="Cari partner keahlian..."
               aria-label="Cari partner keahlian" autocomplete="off" value="${esc(path === "/matching" ? (currentQuery().get("keyword") || "") : "")}">
      </form>
      <div class="navbar-nav me-auto flex-wrap">${links}</div>
      <span class="small text-muted me-3">${esc(currentUser()?.fullName ?? "")}${isAdmin() ? " (admin)" : ""}</span>
      <button id="gk-theme-toggle" type="button" class="gk-icon-btn me-2" aria-label="Ganti tema terang/gelap" title="Ganti tema">
        <i class="bi ${currentTheme() === "dark" ? "bi-sun-fill" : "bi-moon-stars-fill"}" aria-hidden="true"></i>
      </button>
      <button id="gk-logout" class="gk-btn gk-btn--secondary gk-btn--sm"><i class="bi bi-box-arrow-right me-1"></i>Keluar</button>
    </nav>
    <main id="view" class="container py-4" style="max-width:960px"></main>`;
}

async function refreshBadge() {
  const badge = document.getElementById("gk-notif-badge");
  if (!badge || !token.get()) return;
  try {
    const { count } = await api("/api/notifications/unread-count");
    badge.textContent = count;
    badge.hidden = count === 0;
  } catch { /* abaikan; 401 ditangani oleh api.js */ }
}

async function render() {
  const path = currentPath();
  const r = match(path);
  if (!r) { app.innerHTML = `<div class="container py-5">${alertBox("Halaman tidak ditemukan: " + path)}</div>`; return; }

  if (r.auth && !currentUser()) { navigate("/login"); return; }
  if (!r.auth && currentUser() && (path === "/login" || path === "/register")) { navigate("/dashboard"); return; }

  clearInterval(badgeTimer);
  let view;
  if (r.shell) {
    app.innerHTML = shellHtml(path);
    view = document.getElementById("view");
    document.getElementById("gk-theme-toggle").onclick = toggleTheme;
    document.getElementById("gk-nav-search").onsubmit = (ev) => {
      ev.preventDefault();
      const v = new FormData(ev.target).get("keyword").toString().trim();
      navigate("/matching" + (v ? "?keyword=" + encodeURIComponent(v) : ""));
    };
    document.getElementById("gk-logout").onclick = () => { logout(); navigate("/login"); };
    refreshBadge();
    badgeTimer = setInterval(refreshBadge, 45000);
  } else {
    app.innerHTML = '<div id="view"></div>';
    view = document.getElementById("view");
  }

  if (r.admin && !isAdmin()) { view.innerHTML = alertBox("Halaman ini khusus admin."); return; }

  view.innerHTML = skeletonFor(path);
  try {
    await r.handler(view, r.params);
  } catch (e) {
    view.innerHTML = alertBox(e.message || "Terjadi kesalahan");
  }
}

window.addEventListener("hashchange", render);
window.addEventListener("gk:unauthorized", () => navigate("/login"));

await loadMe();
render();