// Perilaku Glass Sidebar: highlight meluncur, collapse/expand (disimpan), penanda menu aktif.
const KEY = "gk-sidebar-collapsed";

export function sidebarCollapsed() {
  try { return localStorage.getItem(KEY) === "1"; } catch { return false; }
}

function moveHl(shell, el) {
  const hl = shell.querySelector(".gs-hl");
  if (!hl || !el) return;
  hl.style.height = el.offsetHeight + "px";
  hl.style.transform = `translateY(${el.offsetTop}px)`;
}
const activeItem = (shell) => shell.querySelector(".gs-item.is-active");

/** Pasang event sekali, tepat setelah shell dimasukkan ke DOM. */
export function initGlassSidebar(shell) {
  const nav = shell.querySelector(".gs-nav");
  const toggle = shell.querySelector(".gs-toggle");
  nav.querySelectorAll(".gs-item").forEach((it) => it.addEventListener("mouseenter", () => moveHl(shell, it)));
  nav.addEventListener("mouseleave", () => moveHl(shell, activeItem(shell)));
  toggle.addEventListener("click", () => {
    const collapsed = !shell.classList.contains("is-collapsed");
    shell.classList.toggle("is-collapsed", collapsed);
    toggle.setAttribute("aria-expanded", String(!collapsed));
    toggle.setAttribute("aria-label", collapsed ? "Perluas sidebar" : "Ciutkan sidebar");
    try { localStorage.setItem(KEY, collapsed ? "1" : "0"); } catch { /* mode privat */ }
    setTimeout(() => moveHl(shell, activeItem(shell)), 360);
  });
  document.fonts?.ready.then(() => moveHl(shell, activeItem(shell)));
}

/** Tandai menu aktif sesuai path; highlight meluncur ke sana (tanpa animasi di pemanggilan pertama). */
export function setActive(shell, path) {
  const on = (href) => path === href || (href !== "/dashboard" && path.startsWith(href + "/"));
  shell.querySelectorAll(".gs-item, .gs-bottom a").forEach((a) => {
    const active = on(a.dataset.href);
    a.classList.toggle("is-active", active);
    active ? a.setAttribute("aria-current", "page") : a.removeAttribute("aria-current");
  });
  const hl = shell.querySelector(".gs-hl");
  const first = !shell.dataset.ready;
  if (first) hl.style.transition = "none";
  moveHl(shell, activeItem(shell));
  if (first) { void hl.offsetWidth; hl.style.transition = ""; shell.dataset.ready = "1"; }
}
