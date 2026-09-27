import { createAsyncThunk } from "@reduxjs/toolkit";

import {
  fetchFileBlob,
  listFiles,
  tunnelStatus,
  uploadFile as uploadFileApi,
} from "../api";

import {
  getCachedBlob,
  putCachedBlob,
  clearCachedBlobs,
} from "../blobCache";

import {
  resolveApiBase,
  suggestedApiBase,
} from "../connection";

import { isImage, isVideo } from "../media";

import type { VaultFile } from "../types";

import type { RootState } from "./index";

import {
  clearVaultState,
  setCacheInfo,
  setError,
  setFiles,
  setLaunchProgress,
  setLaunching,
  setObjectUrl,
  setSettings,
  setTunnelPullActive,
} from "./vaultSlice";

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

  const runners = Array.from(
    { length: Math.min(concurrency, items.length) },
    async () => {
      while (index < items.length) {
        const current = items[index++];
        await worker(current);
      }
    },
  );

  await Promise.all(runners);
}

export const launchVault = createAsyncThunk<
  {
    files: number;
    expiresAt: number;
  },
  void,
  { state: RootState }
>(
  "vault/launchVault",
  async (_, { dispatch, getState }) => {
    const currentSettings = getState().vault.settings;

    // Start launch state
    dispatch(setLaunching(true));
    dispatch(setTunnelPullActive(true));
    dispatch(setError(null));
    dispatch(
      setLaunchProgress("Opening authenticated tunnel…"),
    );

    try {
      // --------------------------------------------------
      // 1. Validate what the user actually entered
      // --------------------------------------------------

      if (!currentSettings.apiBase.trim()) {
        throw new Error(
          "API Base is required. Enter the LifelineOS Core address in Settings.",
        );
      }

      if (!currentSettings.token.trim()) {
        throw new Error(
          "Authentication token is required. Enter your Core token in Settings.",
        );
      }

      // --------------------------------------------------
      // 2. Resolve the API Base only AFTER validation
      // --------------------------------------------------

      const settings = {
        ...currentSettings,
        apiBase: resolveApiBase(
          currentSettings.apiBase,
        ),
      };

      dispatch(
        setSettings({
          apiBase: settings.apiBase,
        }),
      );

      // --------------------------------------------------
      // 3. Check authenticated tunnel
      // --------------------------------------------------

      try {
        await tunnelStatus(settings);
      } catch {
        // tunnel/status is permitAll.
        // If this fails, the host may be wrong.
        // listFiles() below will provide the real error.
      }

      // --------------------------------------------------
      // 4. Authenticate and list vault files
      // --------------------------------------------------

      dispatch(
        setLaunchProgress(
          "Authenticating and listing vault…",
        ),
      );

      const next = await listFiles(settings);

      if (!Array.isArray(next)) {
        throw new Error(
          "Unexpected vault list response from core.",
        );
      }

      // --------------------------------------------------
      // 5. Clear previous local cache
      // --------------------------------------------------

      await clearCachedBlobs();

      // --------------------------------------------------
      // 6. Calculate cache expiration
      // --------------------------------------------------

      const ttlMinutes = Number(
        settings.cacheTtlMinutes,
      );

      const ttlMs = ttlMinutes * 60_000;

      const launchedAt = Date.now();
      const expiresAt = launchedAt + ttlMs;

      // --------------------------------------------------
      // 7. Store vault file list in Redux
      // --------------------------------------------------

      dispatch(setFiles(next));

      dispatch(
        setCacheInfo({
          lastLaunchedAt: launchedAt,
          cacheExpiresAt: expiresAt,
        }),
      );

      // --------------------------------------------------
      // 8. Pull media through the authenticated tunnel
      // --------------------------------------------------

      dispatch(
        setLaunchProgress(
          next.length === 0
            ? "Vault empty — upload from Settings after launch."
            : `Pulling ${next.length} file(s) through the tunnel…`,
        ),
      );

      const media = next.filter(
        (file) => isImage(file) || isVideo(file),
      );

      let done = 0;
      let failed = 0;

      // Maximum 3 files downloaded simultaneously
      await mapPool(media, 3, async (file) => {
        try {
          // Download file from Core
          const blob = await fetchFileBlob(
            settings,
            file.id,
          );

          // Store locally
          await putCachedBlob(
            file.id,
            blob,
            file.sizeBytes,
          );

          // Create browser object URL
          const url = URL.createObjectURL(blob);

          done += 1;

          dispatch(
            setObjectUrl({
              fileId: file.id,
              url,
            }),
          );

          dispatch(
            setLaunchProgress(
              `Secured ${done}/${media.length} media files locally…`,
            ),
          );
        } catch {
          failed += 1;

          dispatch(
            setLaunchProgress(
              `Secured ${done}/${media.length} (skipped ${failed})…`,
            ),
          );
        }
      });

      // --------------------------------------------------
      // 9. Launch completed
      // --------------------------------------------------

      dispatch(setLaunching(false));
      dispatch(setTunnelPullActive(false));
      dispatch(setLaunchProgress(null));

      // Only show an error if ALL media downloads failed
      dispatch(
        setError(
          failed > 0 && done === 0
            ? `Listed ${next.length} files but could not pull media. Check token and tunnel API base (${settings.apiBase}).`
            : null,
        ),
      );

      // --------------------------------------------------
      // 10. Return result to SettingsView
      // --------------------------------------------------

      return {
        files: next.length,
        expiresAt,
      };
    } catch (e) {
      // --------------------------------------------------
      // Launch failed
      // --------------------------------------------------

      dispatch(setLaunching(false));
      dispatch(setTunnelPullActive(false));
      dispatch(setLaunchProgress(null));

      const message = formatReachError(
        currentSettings.apiBase,
        e,
      );

      dispatch(setError(message));

      // SettingsView's .unwrap() will receive this error
      throw new Error(message);
    }
  },
);


export const enforceCachePolicy = createAsyncThunk<
  void,
  void,
  { state: RootState }
>(
  "vault/enforceCachePolicy",
  async (_, { dispatch, getState }) => {
    const { lastLaunchedAt, cacheExpiresAt, objectUrls } =
      getState().vault;

    // No cache has been created yet.
    if (!lastLaunchedAt && cacheExpiresAt == null) {
      return;
    }

    // Cache is still valid.
    if (
      cacheExpiresAt != null &&
      Number.isFinite(cacheExpiresAt) &&
      Date.now() < cacheExpiresAt
    ) {
      return;
    }

    // Revoke temporary browser URLs.
    for (const url of Object.values(objectUrls)) {
      URL.revokeObjectURL(url);
    }

    // Remove locally cached media.
    await clearCachedBlobs();

    // Reset cache-related Redux state.
    dispatch(clearVaultState());

    dispatch(
      setError(
        "Local vault cache expired. Launch Vault through the authenticated tunnel to load again.",
      ),
    );
  },
);

export const ensureBlobUrl = createAsyncThunk<
  { fileId: string; url: string },
  VaultFile,
  { state: RootState }
>(
  "vault/ensureBlobUrl",
  async (file, { dispatch, getState }) => {
    const {
      objectUrls,
      tunnelPullActive,
      settings,
    } = getState().vault;

    const existing = objectUrls[file.id];

    if (existing) {
      return {
        fileId: file.id,
        url: existing,
      };
    }

    const cachedBlob = await getCachedBlob(
      file.id,
      file.sizeBytes,
    );

    if (cachedBlob) {
      const url = URL.createObjectURL(cachedBlob);

      dispatch(
        setObjectUrl({
          fileId: file.id,
          url,
        }),
      );

      return {
        fileId: file.id,
        url,
      };
    }

    if (tunnelPullActive) {
      const remoteBlob = await fetchFileBlob(
        settings,
        file.id,
      );

      await putCachedBlob(
        file.id,
        remoteBlob,
        file.sizeBytes,
      );

      const url = URL.createObjectURL(remoteBlob);

      dispatch(
        setObjectUrl({
          fileId: file.id,
          url,
        }),
      );

      return {
        fileId: file.id,
        url,
      };
    }

    throw new Error(
      "Not in local vault cache — Launch Vault to pull through the tunnel.",
    );
  },
);

export const restoreLocalMedia = createAsyncThunk<
  void,
  void,
  { state: RootState }
>(
  "vault/restoreLocalMedia",
  async (_, { dispatch, getState }) => {
    const { files } = getState().vault;

    const media = files.filter(
      (file) => isImage(file) || isVideo(file),
    );

    await mapPool(media, 3, async (file) => {
      try {
        const blob = await getCachedBlob(
          file.id,
          file.sizeBytes,
        );

        if (!blob) return;

        const url = URL.createObjectURL(blob);

        dispatch(
          setObjectUrl({
            fileId: file.id,
            url,
          }),
        );
      } catch {
        // Ignore individual cache restore failures.
      }
    });
  },
);

export const clearCache = createAsyncThunk<
  void,
  void,
  { state: RootState }
>(
  "vault/clearCache",
  async (_, { dispatch, getState }) => {
    const { objectUrls } = getState().vault;

    for (const url of Object.values(objectUrls)) {
      URL.revokeObjectURL(url);
    }

    await clearCachedBlobs();

    dispatch(clearVaultState());
  },
);

export const uploadFile = createAsyncThunk<
  VaultFile,
  File,
  { state: RootState }
>(
  "vault/uploadFile",
  async (file, { dispatch, getState }) => {
    const state = getState().vault;

    const isCacheValid =
      state.lastLaunchedAt != null &&
      state.cacheExpiresAt != null &&
      Number.isFinite(state.cacheExpiresAt) &&
      Date.now() < state.cacheExpiresAt;

    if (!isCacheValid && !state.tunnelPullActive) {
      throw new Error(
        "Launch Vault first — uploads require an open authenticated tunnel session.",
      );
    }

    const settings = {
      ...state.settings,
      apiBase: resolveApiBase(state.settings.apiBase),
    };

    const next = await uploadFileApi(
      settings,
      file,
    );

    dispatch(
      setFiles([
        next,
        ...state.files,
      ]),
    );

    return next;
  },
);