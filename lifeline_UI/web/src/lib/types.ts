export type VaultNamespace =
  | "DOCUMENTS"
  | "HEALTH_EXPORTS"
  | "BACKUPS"
  | "FINANCE"
  | "GENERAL";

export type VaultFile = {
  id: string;
  displayName: string;
  contentType?: string | null;
  sizeBytes?: number | null;
  namespace?: string | null;
  volumeId?: string | null;
  createdAt?: string | null;
};

export type ConnectionSettings = {
  /** Authenticated tunnel base — core host, never the UI port (5173). */
  apiBase: string;
  token: string;
  namespace: VaultNamespace;
  volumeId: string;
  /** How long local vault cache may be shown before Launch Vault is required again. */
  cacheTtlMinutes: number;
};

export const CACHE_TTL_OPTIONS = [
  { value: 15, label: "15 minutes" },
  { value: 60, label: "1 hour" },
  { value: 360, label: "6 hours" },
  { value: 1440, label: "24 hours" },
  { value: 10080, label: "7 days" },
] as const;
