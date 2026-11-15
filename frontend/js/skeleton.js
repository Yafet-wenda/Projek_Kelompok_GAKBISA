// Kerangka (skeleton) sementara yang tampil selama data halaman diambil.
// Bentuknya meniru tata letak halaman aslinya supaya tidak ada lompatan saat data tiba.

const bar = (w, h = 12, cls = "", mb = 0) =>
  `<div class="gk-skel ${cls}" style="width:${w};height:${h}px;${mb ? `margin-bottom:${mb}px` : ""}"></div>`;

const wrap = (inner) => `
  <div class="gk-skel-wrap" role="status" aria-busy="true" aria-live="polite">
    <span class="visually-hidden">Memuat…</span>
    ${inner}
  </div>`;

const title = (w = "220px") => bar(w, 26, "", 16);

const row = (rightBadge = true) => `
  <div class="d-flex justify-content-between align-items-start py-2 gap-3">
    <div class="flex-grow-1">${bar("45%", 14, "", 8)}${bar("70%", 11, "", 6)}${bar("55%", 11)}</div>
    ${rightBadge ? bar("64px", 22, "gk-skel--pill") : ""}
  </div>`;

const listCard = () => `<div class="gk-card p-3 mb-2">${row()}</div>`;

function dashboard() {
  const stat = `
    <div class="col-md-4">
      <div class="gk-card p-3 h-100">
        ${bar("28px", 28, "gk-skel--circle", 10)}
        ${bar("56px", 30, "", 10)}
        ${bar("75%", 12)}
      </div>
    </div>`;
  const panel = `
    <div class="col-md-6">
      <div class="gk-card p-3">
        ${bar("40%", 16, "", 8)}${row()}${row()}${row()}
      </div>
    </div>`;
  return wrap(`
    ${title("260px")}
    <div class="row g-3 mb-4">${stat}${stat}${stat}</div>
    <div class="row g-3">${panel}${panel}</div>`);
}

function list(withSearch = false) {
  return wrap(`
    ${title("200px")}
    ${withSearch ? bar("100%", 38, "", 16) : ""}
    ${listCard()}${listCard()}${listCard()}`);
}

function profile() {
  return wrap(`
    ${title("160px")}
    <div class="gk-card p-4">
      <div class="d-flex align-items-center gap-3 mb-4">
        ${bar("64px", 64, "gk-skel--circle")}
        <div class="flex-grow-1">${bar("40%", 18, "", 10)}${bar("25%", 12)}</div>
      </div>
      ${bar("100%", 38, "", 14)}${bar("100%", 38, "", 14)}${bar("100%", 90)}
    </div>`);
}

function generic() {
  return wrap(`
    ${title()}
    <div class="gk-card p-3">${bar("60%", 14, "", 10)}${bar("90%", 12, "", 8)}${bar("80%", 12, "", 8)}${bar("50%", 12)}</div>`);
}

/** Skeleton yang cocok untuk path halaman (mis. "/dashboard", "/sessions/abc"). */
export function skeletonFor(path) {
  if (path === "/dashboard") return dashboard();
  if (path === "/matching") return list(true);
  if (path === "/barter" || path === "/sessions" || path === "/notifications") return list();
  if (path === "/profile") return profile();
  return generic();
}