"use client";

import { useEffect, useMemo, useState } from "react";
import { formatDayLabel } from "@/lib/media";
import { useVaultStore } from "@/lib/store";
import type { VaultFile } from "@/lib/types";
import { GalleryTile } from "./GalleryTile";
import { PhotoViewer } from "./PhotoViewer";

function groupByDay(files: VaultFile[]) {
  const groups = new Map<string, VaultFile[]>();
  for (const file of files) {
    const key = formatDayLabel(file.createdAt);
    const list = groups.get(key) ?? [];
    list.push(file);
    groups.set(key, list);
  }
  return [...groups.entries()];
}

function formatRemaining(expiresAt: number | null): string {
  if (!expiresAt || !Number.isFinite(expiresAt)) return "";
  const ms = expiresAt - Date.now();
  if (ms <= 0) return "expired";
  const mins = Math.ceil(ms / 60_000);
  if (mins < 60) return `${mins}m left`;
  const hours = Math.floor(mins / 60);
  const rem = mins % 60;
  return rem ? `${hours}h ${rem}m left` : `${hours}h left`;
}

export function GalleryView() {
  const files = useVaultStore((s) => s.files);
  const launching = useVaultStore((s) => s.launching);
  const launchProgress = useVaultStore((s) => s.launchProgress);
  const error = useVaultStore((s) => s.error);
  const lastLaunchedAt = useVaultStore((s) => s.lastLaunchedAt);
  const cacheExpiresAt = useVaultStore((s) => s.cacheExpiresAt);
  const hydrated = useVaultStore((s) => s.hydrated);
  const ttl = useVaultStore((s) => s.settings.cacheTtlMinutes);
  const apiBase = useVaultStore((s) => s.settings.apiBase);
  const launchVault = useVaultStore((s) => s.launchVault);
  const enforceCachePolicy = useVaultStore((s) => s.enforceCachePolicy);

  const [viewerIndex, setViewerIndex] = useState<number | null>(null);
  const [, setTick] = useState(0);

  const valid =
  lastLaunchedAt != null &&
  cacheExpiresAt != null &&
  Number.isFinite(cacheExpiresAt) &&
  Date.now() < cacheExpiresAt;
   
  useEffect(() => {
    if (!hydrated) return;
    void enforceCachePolicy();
  }, [hydrated, enforceCachePolicy]);

  useEffect(() => {
    if (!cacheExpiresAt || !Number.isFinite(cacheExpiresAt)) return;
    const id = window.setInterval(() => {
      setTick((n) => n + 1);
      if (Date.now() >= cacheExpiresAt) void enforceCachePolicy();
    }, 15_000);
    return () => window.clearInterval(id);
  }, [cacheExpiresAt, enforceCachePolicy]);

  const groups = useMemo(() => groupByDay(files), [files]);
  const flatIndex = useMemo(() => {
    const map = new Map<string, number>();
    files.forEach((f, i) => map.set(f.id, i));
    return map;
  }, [files]);

  return (
    <div className="gallery-page">
      <header className="gallery-header">
        <div>
          <p className="eyebrow">Library</p>
          <h1>Recents</h1>
        </div>
        <button
          type="button"
          className="text-btn"
          onClick={() => void launchVault()}
          disabled={launching}
        >
          {launching ? "Launching…" : "Launch Vault"}
        </button>
      </header>

      <p className="security-note">
        Tunnel: <code>{apiBase}</code> · local TTL {ttl} min · Launch Vault to
        pull; no silent network reads.
      </p>

      {error ? (
        <div className="banner error">
          <p>{error}</p>
          <button type="button" className="text-btn" onClick={() => void launchVault()}>
            Launch Vault
          </button>
        </div>
      ) : null}

      {launching ? (
        <div className="banner">
          <p>{launchProgress || "Launching vault…"}</p>
        </div>
      ) : null}

      {!launching && !valid ? (
        <div className="empty-state vault-lock">
          <h2>
            {lastLaunchedAt && cacheExpiresAt && Date.now() >= cacheExpiresAt
              ? "Cache expired"
              : "Vault sealed"}
          </h2>
          <p>
            Launch Vault to open the authenticated tunnel, pull media once, and
            keep it on this device until the TTL you set in Settings.
          </p>
          <button type="button" className="primary-cta" onClick={() => void launchVault()}>
            Launch Vault
          </button>
        </div>
      ) : null}

      {valid && !launching && files.length === 0 ? (
        <div className="empty-state">
          <h2>No Photos</h2>
          <p>Vault is open but empty — upload from Settings, then Launch Vault again.</p>
        </div>
      ) : null}

      {valid
        ? groups.map(([label, groupFiles]) => (
            <section key={label} className="day-section">
              <h2>{label}</h2>
              <div className="gallery-grid">
                {groupFiles.map((file) => (
                  <GalleryTile
                    key={file.id}
                    file={file}
                    onOpen={() => setViewerIndex(flatIndex.get(file.id) ?? 0)}
                  />
                ))}
              </div>
            </section>
          ))
        : null}

      {valid && lastLaunchedAt ? (
        <p className="cache-note">
          Tunnel sync {new Date(lastLaunchedAt).toLocaleString()} · local{" "}
          {formatRemaining(cacheExpiresAt)} · Launch Vault for new data
        </p>
      ) : null}

      {viewerIndex != null && valid ? (
        <PhotoViewer
          files={files}
          index={viewerIndex}
          onClose={() => setViewerIndex(null)}
          onChangeIndex={setViewerIndex}
        />
      ) : null}
    </div>
  );
}
