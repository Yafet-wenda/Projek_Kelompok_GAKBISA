/* Sentuhan kecil untuk form: tombol lihat/sembunyikan password. */
(function () {
  "use strict";
  document.querySelectorAll(".pw__toggle").forEach((btn) => {
    btn.addEventListener("click", () => {
      const input = btn.parentElement.querySelector("input");
      const tampil = input.type === "password";
      input.type = tampil ? "text" : "password";
      btn.textContent = tampil ? "Sembunyi" : "Lihat";
      btn.setAttribute("aria-pressed", String(tampil));
      input.focus();
    });
  });
})();
