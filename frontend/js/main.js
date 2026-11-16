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
import { initGlassSidebar, setActive, sidebarCollapsed } from "./glass-sidebar.js";

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
  ["/matching", "bi-search", "Cari partner"],
  ["/barter", "bi-arrow-left-right", "Barter keahlian"],
  ["/sessions", "bi-calendar-event", "Sesi belajar"],
  ["/notifications", "bi-bell", "Notifikasi"],
  ["/profile", "bi-person", "Profil saya"],
];

const initials = (n = "") => n.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join("") || "?";
const today = () => new Date().toLocaleDateString("id-ID", { weekday: "long", day: "numeric", month: "long", year: "numeric" });

// Shell dirender SEKALI; pindah halaman hanya mengganti isi #view (animasi sidebar tidak reset).
function shellHtml() {
  const collapsed = sidebarCollapsed();
  const items = NAV.map(([href, icon, label]) => `
    <a href="#${href}" class="gs-item" data-href="${href}" data-tip="${label}">
      <i class="bi ${icon}" aria-hidden="true"></i><span class="gs-fade">${label}</span>
      ${href === "/notifications" ? '<em id="gk-notif-badge" class="gs-badge" hidden>0</em>' : ""}
    </a>`).join("");
  const bottom = NAV.map(([href, icon, label]) =>
    `<a href="#${href}" data-href="${href}"><i class="bi ${icon}" aria-hidden="true"></i>${label.split(" ")[0]}</a>`).join("");
  const u = currentUser();
  return `
  <div class="gs-shell${collapsed ? " is-collapsed" : ""}">
    <aside class="gs-side" aria-label="Navigasi utama">
      <button class="gs-toggle" type="button" aria-expanded="${!collapsed}" aria-label="${collapsed ? "Perluas" : "Ciutkan"} sidebar"><i class="bi bi-chevron-left"></i></button>
      <a class="gs-brand" href="#/dashboard" aria-label="GAKBISA - beranda">
        <span class="gs-mark" aria-hidden="true">G</span>
        <span class="gs-fade"><b>GAKBISA.</b><small>Gantian Ajari, Kita Bisa!</small></span>
      </a>
      <div class="gs-label gs-fade">RUANG BELAJAR</div>
      <nav class="gs-nav"><div class="gs-hl"></div>${items}</nav>
      <div class="gs-me">
        <span class="gs-avatar">${esc(initials(u?.fullName))}</span>
        <span class="gs-fade"><b>${esc(u?.fullName ?? "")}${isAdmin() ? " (admin)" : ""}</b><small>Mahasiswa</small></span>
        <button id="gk-logout" class="gs-logout" type="button" aria-label="Keluar" title="Keluar"><i class="bi bi-box-arrow-right"></i></button>
      </div>
    </aside>
    <div class="gs-body">
      <header class="gs-top">
        <span id="gs-crumb" class="gs-crumb"></span>
        <form id="gk-nav-search" class="gs-search" role="search"><i class="bi bi-search" aria-hidden="true"></i>
          <input name="keyword" type="search" placeholder="Cari partner keahlian..." aria-label="Cari partner keahlian" autocomplete="off"></form>
        <span class="gs-date">${esc(today())}</span>
        <button id="gk-theme-toggle" type="button" class="gs-icon-btn" aria-label="Ganti tema terang/gelap" title="Ganti tema">
          <i class="bi ${currentTheme() === "dark" ? "bi-sun-fill" : "bi-moon-stars-fill"}" aria-hidden="true"></i></button>
      </header>
      <main id="view" class="gs-main"></main>
    </div>
    <nav class="gs-bottom" aria-label="Navigasi bawah">${bottom}</nav>
  </div>`;
}

function mountShell(path) {
  let shell = app.querySelector(".gs-shell");
  if (!shell) {
    app.innerHTML = shellHtml();
    shell = app.querySelector(".gs-shell");
    initGlassSidebar(shell);
    document.getElementById("gk-theme-toggle").onclick = toggleTheme;
    document.getElementById("gk-nav-search").onsubmit = (ev) => {
      ev.preventDefault();
      const v = new FormData(ev.target).get("keyword").toString().trim();
      navigate("/matching" + (v ? "?keyword=" + encodeURIComponent(v) : ""));
    };
    document.getElementById("gk-logout").onclick = () => { logout(); navigate("/login"); };
    refreshBadge();
    badgeTimer = setInterval(refreshBadge, 45000);
  }
  setActive(shell, path);
  const cur = NAV.find(([h]) => path === h || path.startsWith(h + "/"));
  document.getElementById("gs-crumb").textContent = "Ruang belajar" + (cur ? " / " + cur[2] : "");
  document.querySelector("#gk-nav-search input").value = path === "/matching" ? (currentQuery().get("keyword") || "") : "";
  return document.getElementById("view");
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

  let view;
  if (r.shell) {
    view = mountShell(path);
  } else {
    clearInterval(badgeTimer);
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