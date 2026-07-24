const out = document.getElementById("out");
const vaultGrid = document.getElementById("vaultGrid");
const vaultEmpty = document.getElementById("vaultEmpty");
const viewerPanel = document.getElementById("viewerPanel");
const viewerImage = document.getElementById("viewerImage");
const viewerFrame = document.getElementById("viewerFrame");
const viewerMeta = document.getElementById("viewerMeta");

/** @type {Map<string, string>} object URLs for cleanup */
const objectUrls = new Map();
let selectedFile = null;

function cfg() {
  return {
    base: document.getElementById("apiBase").value.replace(/\/$/, ""),
    token: document.getElementById("token").value.trim(),
  };
}

function authHeaders(extra = {}) {
  return { Authorization: `Bearer ${cfg().token}`, ...extra };
}

async function api(path, options = {}) {
  const { base } = cfg();
  const res = await fetch(`${base}${path}`, options);
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  if (!res.ok) {
    throw new Error(typeof body === "string" ? body : JSON.stringify(body, null, 2));
  }
  return body;
}

function show(value) {
  out.textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

function isImage(contentType, name) {
  if (contentType && contentType.startsWith("image/")) return true;
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(name || "");
}

function formatBytes(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function revokeAllObjectUrls() {
  for (const url of objectUrls.values()) URL.revokeObjectURL(url);
  objectUrls.clear();
}

async function fetchContentBlob(id) {
  const res = await fetch(`${cfg().base}/api/files/${id}/content`, {
    headers: authHeaders(),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `Failed to load content (${res.status})`);
  }
  return res.blob();
}

async function loadVault() {
  revokeAllObjectUrls();
  vaultGrid.innerHTML = "";
  const files = await api("/api/files", { headers: authHeaders() });
  if (!Array.isArray(files) || files.length === 0) {
    vaultEmpty.hidden = false;
    vaultEmpty.textContent = "Vault is empty — upload an image or file.";
    vaultGrid.hidden = true;
    show({ count: 0, files: [] });
    return;
  }

  vaultEmpty.hidden = true;
  vaultGrid.hidden = false;
  show({ count: files.length, files });

  for (const file of files) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "vault-card";
    card.dataset.id = file.id;

    const nameEl = document.createElement("div");
    nameEl.className = "name";
    nameEl.textContent = file.displayName || file.id;

    const metaEl = document.createElement("div");
    metaEl.className = "meta";
    metaEl.textContent = `${file.namespace || ""} · ${formatBytes(file.sizeBytes || 0)} · ${file.volumeId || "primary"}`;

    if (isImage(file.contentType, file.displayName)) {
      const img = document.createElement("img");
      img.className = "thumb";
      img.alt = file.displayName || "image";
      img.loading = "lazy";
      card.appendChild(img);
      // Fetch decrypted bytes through the tunnel and show as object URL
      fetchContentBlob(file.id)
        .then((blob) => {
          const url = URL.createObjectURL(blob);
          objectUrls.set(file.id, url);
          img.src = url;
        })
        .catch(() => {
          const fallback = document.createElement("div");
          fallback.className = "thumb-fallback";
          fallback.textContent = "Preview unavailable";
          img.replaceWith(fallback);
        });
    } else {
      const fallback = document.createElement("div");
      fallback.className = "thumb-fallback";
      fallback.textContent = (file.contentType || "file").split(";")[0];
      card.appendChild(fallback);
    }

    card.appendChild(nameEl);
    card.appendChild(metaEl);
    card.addEventListener("click", () => openViewer(file));
    vaultGrid.appendChild(card);
  }
}

async function openViewer(file) {
  selectedFile = file;
  viewerPanel.hidden = false;
  viewerMeta.textContent = `${file.displayName} · ${file.contentType || "unknown"} · ${formatBytes(file.sizeBytes || 0)} · id=${file.id}`;
  viewerImage.hidden = true;
  viewerFrame.hidden = true;
  viewerImage.removeAttribute("src");
  viewerFrame.removeAttribute("src");

  try {
    let url = objectUrls.get(file.id);
    if (!url) {
      const blob = await fetchContentBlob(file.id);
      url = URL.createObjectURL(blob);
      objectUrls.set(file.id, url);
    }

    if (isImage(file.contentType, file.displayName)) {
      viewerImage.src = url;
      viewerImage.hidden = false;
    } else if ((file.contentType || "").includes("pdf") || /\.pdf$/i.test(file.displayName || "")) {
      viewerFrame.src = url;
      viewerFrame.hidden = false;
    } else {
      viewerFrame.src = url;
      viewerFrame.hidden = false;
    }
    viewerPanel.scrollIntoView({ behavior: "smooth", block: "start" });
  } catch (e) {
    show(String(e.message || e));
  }
}

function closeViewer() {
  viewerPanel.hidden = true;
  viewerImage.hidden = true;
  viewerFrame.hidden = true;
  viewerImage.removeAttribute("src");
  viewerFrame.removeAttribute("src");
  selectedFile = null;
}

document.getElementById("btnStatus").onclick = async () => {
  try {
    show(await api("/"));
  } catch (e) {
    show(String(e.message || e));
  }
};

document.getElementById("btnVolumes").onclick = async () => {
  try {
    show(await api("/api/storage/volumes", { headers: authHeaders() }));
  } catch (e) {
    show(String(e.message || e));
  }
};

document.getElementById("btnRefreshVault").onclick = async () => {
  try {
    await loadVault();
  } catch (e) {
    show(String(e.message || e));
  }
};

document.getElementById("btnCloseViewer").onclick = () => closeViewer();

document.getElementById("btnDownload").onclick = () => {
  if (!selectedFile) return;
  const url = objectUrls.get(selectedFile.id);
  if (!url) {
    show("Open the file first so content is loaded");
    return;
  }
  const a = document.createElement("a");
  a.href = url;
  a.download = selectedFile.displayName || selectedFile.id;
  a.click();
};

document.getElementById("btnUpload").onclick = async () => {
  const file = document.getElementById("fileInput").files[0];
  if (!file) {
    show("Choose a file first");
    return;
  }
  const form = new FormData();
  form.append("file", file);
  form.append("namespace", document.getElementById("namespace").value);
  form.append("volumeId", document.getElementById("volumeId").value || "primary");
  try {
    const res = await fetch(`${cfg().base}/api/files`, {
      method: "POST",
      headers: authHeaders(),
      body: form,
    });
    const body = await res.json();
    if (!res.ok) throw new Error(JSON.stringify(body, null, 2));
    show(body);
    document.getElementById("fileInput").value = "";
    await loadVault();
  } catch (e) {
    show(String(e.message || e));
  }
};

document.getElementById("btnMfaStatus").onclick = async () => {
  try {
    show(await api("/api/auth/mfa/status", { headers: authHeaders() }));
  } catch (e) {
    show(String(e.message || e));
  }
};

document.getElementById("btnMfaVerify").onclick = async () => {
  const code = document.getElementById("mfaCode").value.trim();
  try {
    const body = await api("/api/auth/mfa/verify", {
      method: "POST",
      headers: authHeaders({ "Content-Type": "application/json" }),
      body: JSON.stringify({ code }),
    });
    if (body.sessionToken) {
      document.getElementById("token").value = body.sessionToken;
    }
    show(body);
  } catch (e) {
    show(String(e.message || e));
  }
};

document.getElementById("btnBackup").onclick = async () => {
  try {
    show(await api("/api/backup/export", { method: "POST", headers: authHeaders() }));
  } catch (e) {
    show(String(e.message || e));
  }
};

// Auto-load gallery when the page opens (developer mode defaults work out of the box)
loadVault().catch((e) => show(`Vault not loaded yet: ${e.message || e}`));
