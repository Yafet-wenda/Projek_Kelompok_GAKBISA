// Hosting dapat mengisi window.GAKBISA_API_BASE atau meta[name="gakbisa-api-base"] saat deploy.
// Nilai kosong tetap memakai backend lokal untuk pengembangan.
const configured = window.GAKBISA_API_BASE
  || document.querySelector('meta[name="gakbisa-api-base"]')?.content;
export const API_BASE = (configured || "http://localhost:8080").replace(/\/+$/, "");
