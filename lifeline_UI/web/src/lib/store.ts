"use client";

import { create } from "zustand";
import { persist } from "zustand/middleware";
import {
  fetchFileBlob,
  listFiles,
  tunnelStatus,
  uploadFile as uploadFileApi,
} from "./api";
import { getCachedBlob, putCachedBlob, clearCachedBlobs } from "./blobCache";
import { resolveApiBase, suggestedApiBase } from "./connection";
import { isImage, isVideo } from "./media";
import type { ConnectionSettings, VaultFile, VaultNamespace } from "./types";

type VaultState = {
  settings: ConnectionSettings;
  hydrated: boolean;
  files: VaultFile[];
  /** When the last successful Launch Vault finished. */
  lastLaunchedAt: number | null;
  /** Absolute expiry for local vault cache (ms). */
  cacheExpiresAt: number | null;
  launching: boolean;
  launchProgress: string | null;
  error: string | null;
  objectUrls: Record<string, string>;
  /** True only while Launch Vault is actively pulling through the tunnel. */
  tunnelPullActive: boolean;
  setSettings: (patch: Partial<ConnectionSettings>) => void;
  setToken: (token: string) => void;
  syncApiBaseForHost: () => void;
  isCacheValid: () => boolean;
  enforceCachePolicy: () => Promise<void>;
  launchVault: () => Promise<void>;
  restoreLocalMedia: () => Promise<void>;
  ensureBlobUrl: (file: VaultFile) => Promise<string>;
  uploadFile: (file: File) => Promise<VaultFile>;
  clearCache: () => Promise<void>;
  revokeObjectUrls: () => void;
};

const DEFAULT_SETTINGS: ConnectionSettings = {
  apiBase: "http://localhost:8080",
  token: "lifeline-dev-token-change-me",
  namespace: "GENERAL",
  volumeId: "primary",
  cacheTtlMinutes: 60,
};

function normalizeSettings(settings: ConnectionSettings): ConnectionSettings {
  const ttl = Number(settings.cacheTtlMinutes);
  return {
    ...settings,
    apiBase: resolveApiBase(settings.apiBase || DEFAULT_SETTINGS.apiBase),
    token: settings.token || DEFAULT_SETTINGS.token,
    namespace: settings.namespace || DEFAULT_SETTINGS.namespace,
    volumeId: settings.volumeId || DEFAULT_SETTINGS.volumeId,
    cacheTtlMinutes:
      Number.isFinite(ttl) && ttl > 0 ? ttl : DEFAULT_SETTINGS.cacheTtlMinutes,
  };
}

function formatReachError(apiBase: string, err: unknown): string {
  const message = err instanceof Error ? err.message : String(err);
  const looksNetwork =
    /failed to fetch|networkerror|load failed|network request failed|unreachable/i.test(
      message,
    );
  if (!looksNetwork) return message;
  const hint = suggestedApiBase();
  return `Tunnel unreachable at ${apiBase}. Point API base at the lifelineOS core (e.g. ${hint}), not the UI port 5173.`;
}

async function mapPool<T>(
  items: T[],
  concurrency: number,
  worker: (item: T) => Promise<void>,
) {
  let index = 0;
  const runners = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (index < items.length) {
      const current = items[index++];
      await worker(current);
    }
  });
  await Promise.all(runners);
}

export const useVaultStore = create<VaultState>()(
  persist(
    (set, get) => ({
      settings: DEFAULT_SETTINGS,
      hydrated: false,
      files: [],
      lastLaunchedAt: null,
      cacheExpiresAt: null,
      launching: false,
      launchProgress: null,
      error: null,
      objectUrls: {},
      tunnelPullActive: false,

      setSettings: (patch) =>
        set((state) => ({
          settings: normalizeSettings({ ...state.settings, ...patch }),
        })),

      setToken: (token) =>
        set((state) => ({
          settings: normalizeSettings({ ...state.settings, token }),
        })),

      syncApiBaseForHost: () => {
        const { settings } = get();
        const next = normalizeSettings(settings);
        if (JSON.stringify(next) !== JSON.stringify(settings)) {
          set({ settings: next });
        }
      },

      isCacheValid: () => {
        const { cacheExpiresAt, lastLaunchedAt } = get();
        if (!lastLaunchedAt || cacheExpiresAt == null) return false;
        if (!Number.isFinite(cacheExpiresAt)) return false;
        return Date.now() < cacheExpiresAt;
      },

      revokeObjectUrls: () => {
        const { objectUrls } = get();
        for (const url of Object.values(objectUrls)) URL.revokeObjectURL(url);
        set({ objectUrls: {} });
      },

      clearCache: async () => {
        get().revokeObjectUrls();
        await clearCachedBlobs();
        set({
          files: [],
          lastLaunchedAt: null,
          cacheExpiresAt: null,
          error: null,
          launchProgress: null,
        });
      },

      enforceCachePolicy: async () => {
        const { cacheExpiresAt, lastLaunchedAt } = get();
        if (!lastLaunchedAt && cacheExpiresAt == null) return;
        if (get().isCacheValid()) return;
        // TTL elapsed or corrupt expiry — wipe local vault material
        await get().clearCache();
        set({
          error:
            "Local vault cache expired. Launch Vault through the authenticated tunnel to load again.",
        });
      },

      restoreLocalMedia: async () => {
        if (!get().isCacheValid()) return;
        const media = get().files.filter((f) => isImage(f) || isVideo(f));
        await mapPool(media, 4, async (file) => {
          try {
            await get().ensureBlobUrl(file);
          } catch {
            /* missing blob until next Launch Vault */
          }
        });
      },

      launchVault: async () => {
        get().syncApiBaseForHost();
        const { settings } = get();

        set({
          launching: true,
          tunnelPullActive: true,
          error: null,
          launchProgress: "Opening authenticated tunnel…",
        });

        try {
          try {
            await tunnelStatus(settings);
          } catch {
            // status is permitAll; failure usually means wrong host
          }

          set({ launchProgress: "Authenticating and listing vault…" });
          const next = await listFiles(settings);
          if (!Array.isArray(next)) {
            throw new Error("Unexpected vault list response from core");
          }

          // Replace previous cache entirely on each launch
          get().revokeObjectUrls();
          await clearCachedBlobs();

          const ttlMinutes = normalizeSettings(settings).cacheTtlMinutes;
          const ttlMs = ttlMinutes * 60_000;
          const launchedAt = Date.now();
          const expiresAt = launchedAt + ttlMs;

          set({
            files: next,
            lastLaunchedAt: launchedAt,
            cacheExpiresAt: expiresAt,
            launchProgress:
              next.length === 0
                ? "Vault empty — upload from Settings after launch."
                : `Pulling ${next.length} file(s) through the tunnel…`,
          });

          const media = next.filter((f) => isImage(f) || isVideo(f));
          let done = 0;
          let failed = 0;
          await mapPool(media, 3, async (file) => {
            try {
              const blob = await fetchFileBlob(settings, file.id);
              await putCachedBlob(file.id, blob, file.sizeBytes);
              const url = URL.createObjectURL(blob);
              set((state) => ({
                objectUrls: { ...state.objectUrls, [file.id]: url },
                launchProgress: `Secured ${++done}/${media.length} media files locally…`,
              }));
            } catch {
              failed += 1;
              set({
                launchProgress: `Secured ${done}/${media.length} (skipped ${failed})…`,
              });
            }
          });

          set({
            launching: false,
            tunnelPullActive: false,
            launchProgress: null,
            error:
              failed > 0 && done === 0
                ? `Listed ${next.length} files but could not pull media. Check token and tunnel API base (${settings.apiBase}).`
                : null,
          });
        } catch (e) {
          set({
            launching: false,
            tunnelPullActive: false,
            launchProgress: null,
            error: formatReachError(settings.apiBase, e),
          });
        }
      },

      ensureBlobUrl: async (file) => {
        const existing = get().objectUrls[file.id];
        if (existing) return existing;

        // Local-only after launch — never silent network pulls
        const blob = await getCachedBlob(file.id, file.sizeBytes);
        if (blob) {
          const url = URL.createObjectURL(blob);
          set((state) => ({
            objectUrls: { ...state.objectUrls, [file.id]: url },
          }));
          return url;
        }

        if (get().tunnelPullActive) {
          const remote = await fetchFileBlob(get().settings, file.id);
          await putCachedBlob(file.id, remote, file.sizeBytes);
          const url = URL.createObjectURL(remote);
          set((state) => ({
            objectUrls: { ...state.objectUrls, [file.id]: url },
          }));
          return url;
        }

        throw new Error("Not in local vault cache — Launch Vault to pull through the tunnel.");
      },

      uploadFile: async (file) => {
        get().syncApiBaseForHost();
        if (!get().isCacheValid() && !get().tunnelPullActive) {
          throw new Error("Launch Vault first — uploads require an open authenticated tunnel session.");
        }
        const created = await uploadFileApi(get().settings, file);
        // Upload goes through tunnel now; require another Launch Vault to refresh gallery cache
        return created;
      },
    }),
    {
      name: "lifeline-vault-store",
      partialize: (state) => ({
        settings: state.settings,
        files: state.files,
        lastLaunchedAt: state.lastLaunchedAt,
        cacheExpiresAt: state.cacheExpiresAt,
      }),
      merge: (persisted, current) => {
        const p = (persisted ?? {}) as Partial<VaultState> & {
          lastFetchedAt?: number | null;
        };
        const settings = normalizeSettings({
          ...current.settings,
          ...(p.settings ?? {}),
        });
        const cacheExpiresAt = p.cacheExpiresAt ?? null;
        const lastLaunchedAt = p.lastLaunchedAt ?? p.lastFetchedAt ?? null;
        return {
          ...current,
          ...p,
          settings,
          lastLaunchedAt,
          cacheExpiresAt:
            cacheExpiresAt != null && Number.isFinite(cacheExpiresAt)
              ? cacheExpiresAt
              : null,
          objectUrls: {},
          launching: false,
          tunnelPullActive: false,
          launchProgress: null,
        };
      },
      onRehydrateStorage: () => () => {
        useVaultStore.setState({ hydrated: true });
        const store = useVaultStore.getState();
        store.syncApiBaseForHost();
        void store.enforceCachePolicy().then(() => store.restoreLocalMedia());
      },
    },
  ),
);

export function updateNamespace(namespace: VaultNamespace) {
  useVaultStore.getState().setSettings({ namespace });
}

export function updateVolumeId(volumeId: string) {
  useVaultStore.getState().setSettings({ volumeId });
}
