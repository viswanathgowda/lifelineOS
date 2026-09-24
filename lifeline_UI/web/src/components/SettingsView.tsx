"use client";

import { useEffect, useState } from "react";
import {
  exportBackup,
  listVolumes,
  mfaStatus,
  mfaVerify,
  NAMESPACES,
  pingCore,
  tunnelStatus,
  VOLUME_OPTIONS,
} from "@/lib/api";
import { isLoopbackApiBase, suggestedApiBase } from "@/lib/connection";
import { useVaultStore } from "@/lib/store";
import { CACHE_TTL_OPTIONS, type VaultNamespace } from "@/lib/types";

export function SettingsView() {
  const settings = useVaultStore((s) => s.settings);
  const setSettings = useVaultStore((s) => s.setSettings);
  const setToken = useVaultStore((s) => s.setToken);
  const uploadFile = useVaultStore((s) => s.uploadFile);
  const clearCache = useVaultStore((s) => s.clearCache);
  const launchVault = useVaultStore((s) => s.launchVault);
  const cacheExpiresAt = useVaultStore((s) => s.cacheExpiresAt);
  const lastLaunchedAt = useVaultStore((s) => s.lastLaunchedAt);
  const isCacheValid = useVaultStore((s) => s.isCacheValid);

  const [file, setFile] = useState<File | null>(null);
  const [mfaCode, setMfaCode] = useState("");
  const [log, setLog] = useState("Ready.");
  const [busy, setBusy] = useState(false);
  const [suggested, setSuggested] = useState("http://localhost:8080");
  const [needsLanHint, setNeedsLanHint] = useState(false);

  useEffect(() => {
    const next = suggestedApiBase();
    setSuggested(next);
    setNeedsLanHint(
      isLoopbackApiBase(settings.apiBase) &&
        typeof window !== "undefined" &&
        !["localhost", "127.0.0.1"].includes(window.location.hostname),
    );
  }, [settings.apiBase]);

  const show = (value: unknown) => {
    setLog(typeof value === "string" ? value : JSON.stringify(value, null, 2));
  };

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true);
    try {
      show(await fn());
    } catch (e) {
      show(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="settings-page">
      <header className="settings-header">
        <p className="eyebrow">lifelineOS</p>
        <h1>Settings</h1>
      </header>

      <section className="settings-group">
        <h2>Authenticated tunnel</h2>
        {needsLanHint ? (
          <div className="banner error" style={{ marginBottom: "0.85rem" }}>
            <p>
              API base is localhost — on a phone that is the phone itself. Use the
              PC core address <strong>{suggested}</strong> (port 8080 / 8443), not
              UI port 5173.
            </p>
            <button
              type="button"
              className="text-btn"
              onClick={() => setSettings({ apiBase: suggested })}
            >
              Fix
            </button>
          </div>
        ) : null}
        <label>
          Tunnel API base (lifelineOS core)
          <input
            value={settings.apiBase}
            onChange={(e) => setSettings({ apiBase: e.target.value })}
            placeholder={suggested}
          />
        </label>
        <p className="hint">
          Vault traffic goes only to the core tunnel (Bearer / MFA session / mTLS).
          Never point this at the UI shell (:5173).
        </p>
        <label>
          Bearer / MFA session token
          <input
            type="password"
            value={settings.token}
            onChange={(e) => setSettings({ token: e.target.value })}
          />
        </label>
        <div className="btn-row">
          <button
            type="button"
            disabled={busy}
            onClick={() => run(() => tunnelStatus(settings))}
          >
            Tunnel status
          </button>
          <button type="button" disabled={busy} onClick={() => run(() => pingCore(settings))}>
            Ping core
          </button>
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => run(() => listVolumes(settings))}
          >
            Volumes
          </button>
        </div>
        <button
          type="button"
          disabled={busy}
          onClick={() =>
            run(async () => {
              await launchVault();
              const s = useVaultStore.getState();
              if (s.error) throw new Error(s.error);
              return {
                ok: true,
                files: s.files.length,
                expiresAt: s.cacheExpiresAt
                  ? new Date(s.cacheExpiresAt).toISOString()
                  : null,
              };
            })
          }
        >
          Launch Vault
        </button>
      </section>

      <section className="settings-group">
        <h2>Local cache TTL</h2>
        <p className="hint">
          After Launch Vault, media stays on this device until TTL ends — then it
          is wiped and Launch Vault is required again. New remote data is never
          pulled silently.
        </p>
        <label>
          Keep local vault for
          <select
            value={settings.cacheTtlMinutes}
            onChange={(e) =>
              setSettings({ cacheTtlMinutes: Number(e.target.value) })
            }
          >
            {CACHE_TTL_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>
        <p className="hint">
          {lastLaunchedAt
            ? `Last launch ${new Date(lastLaunchedAt).toLocaleString()} · ${
                isCacheValid()
                  ? `expires ${cacheExpiresAt ? new Date(cacheExpiresAt).toLocaleString() : "—"}`
                  : "expired — launch again"
              }`
            : "Not launched yet on this device."}
        </p>
        <p className="hint">
          Changing TTL applies to the next Launch Vault (current expiry stays until
          then, or Clear cache).
        </p>
        <button
          type="button"
          className="secondary"
          disabled={busy}
          onClick={() =>
            run(() => clearCache().then(() => "Local vault cache wiped"))
          }
        >
          Clear local cache now
        </button>
      </section>

      <section className="settings-group">
        <h2>Upload (tunnel)</h2>
        <p className="hint">
          Requires a non-expired Launch Vault session. Launch again afterward to
          refresh the gallery cache.
        </p>
        <label>
          Namespace
          <select
            value={settings.namespace}
            onChange={(e) =>
              setSettings({ namespace: e.target.value as VaultNamespace })
            }
          >
            {NAMESPACES.map((ns) => (
              <option key={ns} value={ns}>
                {ns}
              </option>
            ))}
          </select>
        </label>
        <label>
          Volume
          <select
            value={settings.volumeId}
            onChange={(e) => setSettings({ volumeId: e.target.value })}
          >
            {VOLUME_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          File
          <input
            type="file"
            accept="image/*,video/*,.pdf,.txt,.json,.csv,.zip,.mp4,.mov"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <button
          type="button"
          disabled={busy || !file}
          onClick={() =>
            run(async () => {
              if (!file) throw new Error("Choose a file first");
              const result = await uploadFile(file);
              setFile(null);
              return result;
            })
          }
        >
          Upload to vault
        </button>
      </section>

      <section className="settings-group">
        <h2>MFA & backup</h2>
        <div className="btn-row">
          <button type="button" disabled={busy} onClick={() => run(() => mfaStatus(settings))}>
            MFA status
          </button>
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => run(() => exportBackup(settings))}
          >
            Export backup
          </button>
        </div>
        <label>
          TOTP code
          <input
            value={mfaCode}
            maxLength={6}
            placeholder="123456"
            onChange={(e) => setMfaCode(e.target.value)}
          />
        </label>
        <button
          type="button"
          disabled={busy}
          onClick={() =>
            run(async () => {
              const body = await mfaVerify(settings, mfaCode.trim());
              if (body.sessionToken) setToken(body.sessionToken);
              return body;
            })
          }
        >
          Verify MFA → session
        </button>
      </section>

      <section className="settings-group">
        <h2>Log</h2>
        <pre className="log">{log}</pre>
      </section>
    </div>
  );
}
