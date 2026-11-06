/* Sentuhan kecil untuk form auth: lihat/sembunyikan password dan pengaman
   klik ganda. Dipakai hanya oleh templates/auth/login.html dan register.html. */
(function () {
  "use strict";

  document.querySelectorAll(".gk-auth__toggle").forEach(function (btn) {
    var input = btn.parentElement.querySelector("input");
    btn.setAttribute("aria-label", "Tampilkan password");
    btn.addEventListener("click", function () {
      var tampil = input.type === "password";
      input.type = tampil ? "text" : "password";
      btn.textContent = tampil ? "Sembunyi" : "Lihat";
      btn.setAttribute("aria-pressed", String(tampil));
      btn.setAttribute("aria-label", tampil ? "Sembunyikan password" : "Tampilkan password");
      input.focus();
    });
  });

  /* Password yang sudah dibuka saat halaman di-render ulang karena error
     validasi harus tetap terbuka; kalau tidak, isinya terbaca samar-samar. */
  document.querySelectorAll(".gk-auth__pw").forEach(function (wrap) {
    var input = wrap.querySelector("input");
    var btn = wrap.querySelector(".gk-auth__toggle");
    if (input && btn && input.type === "text") {
      btn.textContent = "Sembunyi";
      btn.setAttribute("aria-pressed", "true");
      btn.setAttribute("aria-label", "Sembunyikan password");
    }
  });

  document.querySelectorAll("form.gk-auth__fields").forEach(function (form) {
    form.addEventListener("submit", function () {
      if (!form.checkValidity()) return;
      var btn = form.querySelector(".gk-auth__submit");
      if (!btn) return;
      btn.disabled = true;
      btn.setAttribute("aria-busy", "true");
    });
  });
})();
