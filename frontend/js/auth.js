import { api, token } from "./api.js";

let me = null;

export const currentUser = () => me;
export const isAdmin = () => me?.role === "ADMIN";

export async function loadMe() {
  if (!token.get()) { me = null; return null; }
  try {
    me = await api("/api/me");
  } catch (e) {
    me = null;
    if (e.status === 401) token.clear();
  }
  return me;
}

export async function login(email, password) {
  const r = await api("/api/auth/login", { method: "POST", body: { email, password } });
  token.set(r.accessToken);
  me = r.user;
  return me;
}

export async function register({ fullName, email, password, campus }) {
  await api("/api/register", { method: "POST", body: { fullName, email, password, campus: campus || null } });
  return login(email, password);
}

export function logout() {
  token.clear();
  me = null;
}
