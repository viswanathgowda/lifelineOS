import type { ConnectionSettings, VaultFile, VaultNamespace } from "./types";

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(status: number, body: unknown) {
    super(typeof body === "string" ? body : JSON.stringify(body));
    this.status = status;
    this.body = body;
  }
}

function authHeaders(token: string, extra: HeadersInit = {}): HeadersInit {
  return { Authorization: `Bearer ${token}`, ...extra };
}

async function parseBody(res: Response): Promise<unknown> {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function apiJson<T>(
  settings: Pick<ConnectionSettings, "apiBase" | "token">,
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const base = settings.apiBase.replace(/\/$/, "");
  const headers = new Headers(options.headers);
  if (settings.token) headers.set("Authorization", `Bearer ${settings.token}`);

  const res = await fetch(`${base}${path}`, { ...options, headers });
  const body = await parseBody(res);
  if (!res.ok) throw new ApiError(res.status, body);
  return body as T;
}

export function listFiles(settings: Pick<ConnectionSettings, "apiBase" | "token">) {
  return apiJson<VaultFile[]>(settings, "/api/files");
}

export async function fetchFileBlob(
  settings: Pick<ConnectionSettings, "apiBase" | "token">,
  id: string,
): Promise<Blob> {
  const base = settings.apiBase.replace(/\/$/, "");
  const res = await fetch(`${base}/api/files/${id}/content`, {
    headers: authHeaders(settings.token),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new ApiError(res.status, text || `Failed to load content (${res.status})`);
  }
  return res.blob();
}

export async function uploadFile(
  settings: ConnectionSettings,
  file: File,
): Promise<VaultFile> {
  const form = new FormData();
  form.append("file", file);
  form.append("namespace", settings.namespace);
  form.append("volumeId", settings.volumeId || "primary");

  const base = settings.apiBase.replace(/\/$/, "");
  const res = await fetch(`${base}/api/files`, {
    method: "POST",
    headers: authHeaders(settings.token),
    body: form,
  });
  const body = await parseBody(res);
  if (!res.ok) throw new ApiError(res.status, body);
  return body as VaultFile;
}

export async function deleteFile(
  settings: Pick<ConnectionSettings, "apiBase" | "token">,
  id: string,
) {
  await apiJson(settings, `/api/files/${id}`, { method: "DELETE" });
}

export function pingCore(settings: Pick<ConnectionSettings, "apiBase" | "token">) {
  return apiJson(settings, "/");
}

export function listVolumes(settings: Pick<ConnectionSettings, "apiBase" | "token">) {
  return apiJson(settings, "/api/storage/volumes");
}

export function mfaStatus(settings: Pick<ConnectionSettings, "apiBase" | "token">) {
  return apiJson(settings, "/api/auth/mfa/status");
}

export function mfaVerify(
  settings: Pick<ConnectionSettings, "apiBase" | "token">,
  code: string,
) {
  return apiJson<{ sessionToken?: string }>(settings, "/api/auth/mfa/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  });
}

export function exportBackup(settings: Pick<ConnectionSettings, "apiBase" | "token">) {
  return apiJson(settings, "/api/backup/export", { method: "POST" });
}

export function tunnelStatus(settings: Pick<ConnectionSettings, "apiBase" | "token">) {
  // Public on core — still go through tunnel host, never UI :5173
  return apiJson(settings, "/api/tunnel/status");
}

export const NAMESPACES: VaultNamespace[] = [
  "GENERAL",
  "DOCUMENTS",
  "HEALTH_EXPORTS",
  "BACKUPS",
  "FINANCE",
];

export const VOLUME_OPTIONS = [
  { value: "primary", label: "Primary" },
  { value: "memory-card", label: "Memory card" },
] as const;
