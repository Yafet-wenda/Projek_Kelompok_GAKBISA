const routes = [];

/** route("/sessions/:id", handler, { auth: true, admin: false, shell: true }) */
export function route(pattern, handler, { auth = true, admin = false, shell = true } = {}) {
  const keys = [];
  const re = new RegExp("^" + pattern.replace(/:([a-zA-Z]+)/g, (_, k) => { keys.push(k); return "([^/]+)"; }) + "$");
  routes.push({ re, keys, handler, auth, admin, shell });
}

export function match(path) {
  for (const r of routes) {
    const m = r.re.exec(path);
    if (m) return { ...r, params: Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])])) };
  }
  return null;
}

export const navigate = (path) => { location.hash = "#" + path; };
// Hash bisa memuat query, mis. "#/matching?keyword=python". Rute dicocokkan dengan path-nya saja.
export const currentPath = () => (location.hash.slice(1) || "/dashboard").split("?")[0];
export const currentQuery = () => new URLSearchParams(location.hash.split("?")[1] || "");