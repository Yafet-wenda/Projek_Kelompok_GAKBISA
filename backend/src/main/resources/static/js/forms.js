/* Sentuhan kecil untuk formulir autentikasi: lihat/sembunyikan password dan
   pengaman klik ganda. Dipakai oleh templates/auth/login.html dan register.html. */
(function () {
  "use strict";

  document.querySelectorAll(".pwd-toggle").forEach(function (btn) {
    var wrap = btn.closest(".pwd");
    var input = wrap && wrap.querySelector("input");
    if (!input) return;

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

  document.querySelectorAll("form.auth-form").forEach(function (form) {
    form.addEventListener("submit", function () {
      if (!form.checkValidity()) return;
      var btn = form.querySelector("button[type='submit']");
      if (!btn) return;
      btn.disabled = true;
      btn.setAttribute("aria-busy", "true");
    });
  });
})();
