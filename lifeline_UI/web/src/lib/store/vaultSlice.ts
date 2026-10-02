import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import type {
  ConnectionSettings,
  VaultFile,
} from "../types";

type VaultState = {
  settings: ConnectionSettings;

  hydrated: boolean;

  files: VaultFile[];

  lastLaunchedAt: number | null;

  cacheExpiresAt: number | null;

  launching: boolean;

  launchProgress: string | null;

  error: string | null;

  objectUrls: Record<string, string>;

  tunnelPullActive: boolean;
};

const DEFAULT_SETTINGS: ConnectionSettings = {
  apiBase: "http://localhost:8080",
  token: "lifeline-dev-token-change-me",
  namespace: "GENERAL",
  volumeId: "primary",
  cacheTtlMinutes: 60,
};

const initialState: VaultState = {
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
};

const vaultSlice = createSlice({
  name: "vault",

  initialState,

  reducers: {
    setSettings(
      state,
      action: PayloadAction<Partial<ConnectionSettings>>,
    ) {
      state.settings = {
        ...state.settings,
        ...action.payload,
      };
    },

    setToken(state, action: PayloadAction<string>) {
      state.settings.token = action.payload;
    },

    setHydrated(state, action: PayloadAction<boolean>) {
      state.hydrated = action.payload;
    },

    setObjectUrl(
      state,
      action: PayloadAction<{ fileId: string; url: string }>,
    ) {
      state.objectUrls[action.payload.fileId] = action.payload.url;
    },

    setFiles(state, action: PayloadAction<VaultFile[]>) {
      state.files = action.payload;
    },

    setCacheInfo(
      state,
      action: PayloadAction<{
        lastLaunchedAt: number;
        cacheExpiresAt: number;
      }>,
    ) {
      state.lastLaunchedAt = action.payload.lastLaunchedAt;
      state.cacheExpiresAt = action.payload.cacheExpiresAt;
    },

    setLaunching(state, action: PayloadAction<boolean>) {
      state.launching = action.payload;
    },

    setTunnelPullActive(state, action: PayloadAction<boolean>) {
      state.tunnelPullActive = action.payload;
    },

    setLaunchProgress(
      state,
      action: PayloadAction<string | null>,
    ) {
      state.launchProgress = action.payload;
    },

    setError(state, action: PayloadAction<string | null>) {
      state.error = action.payload;
    },

    clearVaultState(state) {
      state.files = [];
      state.lastLaunchedAt = null;
      state.cacheExpiresAt = null;
      state.error = null;
      state.launchProgress = null;
      state.objectUrls = {};
    },
  },
});

export const {
  setSettings,
  setToken,
  setHydrated,
  setObjectUrl,
  setFiles,
  setCacheInfo,
  setLaunching,
  setTunnelPullActive,
  setLaunchProgress,
  setError,
  clearVaultState,
} = vaultSlice.actions;

export default vaultSlice.reducer;