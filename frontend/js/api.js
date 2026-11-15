import { API_BASE } from "./config.js";

const KEY = "gk-token";

// Cache singkat untuk GET: pindah-pindah halaman dalam beberapa detik tidak mengulang
// request yang sama ke backend, dan request kembar yang sedang berjalan digabung jadi satu.
// Cache dikosongkan setiap ada tulis (POST/PUT/DELETE...) dan saat token berganti.
const CACHE_TTL_MS = 10_000;
const cache = new Map(); // url -> { expires, promise }

export const token = {
  get: () => localStorage.getItem(KEY),
  set: (v) => { cache.clear(); localStorage.setItem(KEY, v); },
  clear: () => { cache.clear(); localStorage.removeItem(KEY); },
};

export class ApiError extends Error {
  constructor(status, message, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

/**
 * Satu-satunya pintu keluar ke backend.
 * api("/api/sessions")                          -> GET
 * api("/api/barter", {method:"POST", body:{..}}) -> JSON
 * api("/api/portfolios", {method:"POST", form})  -> multipart (FormData)
 */
export async function api(path, { method = "GET", body, query, form } = {}) {
  const url = new URL(API_BASE + path);
  if (query) for (const [k, v] of Object.entries(query)) if (v != null) url.searchParams.set(k, v);

  if (method === "GET" && !form) {
    const key = url.toString();
    const hit = cache.get(key);
    if (hit && hit.expires > Date.now()) return structuredClone(await hit.promise);
    const promise = request(url, method, body, form);
    cache.set(key, { expires: Date.now() + CACHE_TTL_MS, promise });
    promise.catch(() => cache.delete(key)); // galat tidak ikut disimpan
    return structuredClone(await promise);
  }

  try {
    return await request(url, method, body, form);
  } finally {
    cache.clear(); // data mungkin berubah; ambil ulang pada GET berikutnya
  }
}

async function request(url, method, body, form) {
  const headers = { Accept: "application/json" };
  const t = token.get();
  if (t) headers.Authorization = "Bearer " + t;

  let payload;
  if (form) payload = form; // biarkan browser mengisi boundary multipart
  else if (body !== undefined) {
    headers["Content-Type"] = "application/json";
    payload = JSON.stringify(body);
  }

  let res;
  try {
    res = await fetch(url, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, "Tidak dapat menghubungi server. Pastikan backend berjalan di " + API_BASE);
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  if (!res.ok) {
    if (res.status === 401 && t) {
      token.clear();
      window.dispatchEvent(new Event("gk:unauthorized"));
    }
    const msg = (data && (data.detail || data.message)) || res.statusText || "Permintaan gagal";
    throw new ApiError(res.status, msg, data);
  }
  return data;
}