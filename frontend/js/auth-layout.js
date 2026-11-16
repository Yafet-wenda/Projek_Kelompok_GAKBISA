// Layout bersama halaman Masuk & Daftar: panel kiri (hero) + panel kanan (form).
import { esc } from "./ui.js";

const HERO = `
  <section class="ga-hero" aria-hidden="false">
    <div class="ga-brand"><b>GAKBISA.</b><small>Gantian Ajari, Kita Bisa!</small></div>
    <div>
      <p class="ga-eyebrow">KOMUNITAS BELAJAR MAHASISWA</p>
      <h2 class="ga-headline">Kamu punya ilmu.<br>Mereka juga.<br>Yuk, saling ajari.</h2>
      <p class="ga-lead">Temukan partner, tukar keahlian, dan tumbuh bersama.<br>Karena belajar nggak harus sendirian.</p>
      <div class="ga-swap">
        <div><small>AKU BISA</small><b>Python</b></div>
        <i class="bi bi-arrow-left-right" aria-hidden="true"></i>
        <div><small>AKU MAU BELAJAR</small><b>UI Design</b></div>
      </div>
    </div>
    <div>
      <ol class="ga-steps"><li><span>01</span>Cari partner</li><li><span>02</span>Tukar keahlian</li><li><span>03</span>Belajar bareng</li></ol>
      <p class="ga-foot">Dari “gak bisa”, jadi bisa bersama.</p>
    </div>
  </section>`;

export function authLayout({ eyebrow, title, subtitle, body }) {
  return `
  <div class="ga">
    ${HERO}
    <section class="ga-side">
      <div class="ga-form">
        <p class="ga-eyebrow ga-eyebrow--blue">${esc(eyebrow)}</p>
        <h1>${esc(title)}</h1>
        <p class="ga-sub">${esc(subtitle)}</p>
        <div id="msg" aria-live="polite"></div>
        ${body}
      </div>
    </section>
  </div>`;
}

/** Satu kolom isian. type="password" otomatis dapat tombol lihat/sembunyikan. */
export function authField({ label, name, type = "text", attrs = "", placeholder = "" }) {
  const eye = type === "password"
    ? `<button type="button" class="ga-eye" aria-label="Tampilkan password" aria-pressed="false"><i class="bi bi-eye"></i></button>` : "";
  return `<label class="ga-field"><span>${esc(label)}</span>
    <span class="ga-input"><input name="${name}" type="${type}" placeholder="${esc(placeholder)}" ${attrs}>${eye}</span></label>`;
}

export function bindAuthForm(view, onSubmit, busyText) {
  view.querySelectorAll(".ga-eye").forEach((btn) => {
    btn.onclick = () => {
      const input = btn.previousElementSibling, show = input.type === "password";
      input.type = show ? "text" : "password";
      btn.setAttribute("aria-pressed", String(show));
      btn.setAttribute("aria-label", show ? "Sembunyikan password" : "Tampilkan password");
      btn.firstElementChild.className = "bi " + (show ? "bi-eye-slash" : "bi-eye");
    };
  });
  const form = view.querySelector("#f"), btn = form.querySelector(".ga-submit"), label = btn.innerHTML;
  form.onsubmit = async (ev) => {
    ev.preventDefault();
    btn.disabled = true; btn.textContent = busyText;
    try { await onSubmit(new FormData(form)); }
    catch (e) {
      view.querySelector("#msg").innerHTML = `<div class="gk-alert gk-alert--danger" role="alert">${esc(e.message || "Terjadi kesalahan")}</div>`;
      btn.disabled = false; btn.innerHTML = label;
    }
  };
  form.querySelector("input")?.focus();
}
