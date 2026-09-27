"use client";

import { useEffect } from "react";

import { useAppDispatch, useAppSelector } from "@/lib/store/hooks";
import { setSettings } from "@/lib/store/vaultSlice";
import {
  enforceCachePolicy,
  restoreLocalMedia,
} from "@/lib/store/vaultThunks";

import { suggestedApiBase } from "@/lib/connection";

import { InstallBanner } from "./InstallBanner";
import { TabBar } from "./TabBar";

export function AppShell({
  children,
}: {
  children: React.ReactNode;
}) {
  const dispatch = useAppDispatch();

  const hydrated = useAppSelector(
    (state) => state.vault.hydrated,
  );

  const lastLaunchedAt = useAppSelector(
    (state) => state.vault.lastLaunchedAt,
  );

  const cacheExpiresAt = useAppSelector(
    (state) => state.vault.cacheExpiresAt,
  );

  useEffect(() => {
    dispatch(
      setSettings({
        apiBase: suggestedApiBase(),
      }),
    );

    void dispatch(enforceCachePolicy());
  }, [dispatch]);

  useEffect(() => {
    if (!hydrated) return;

    const cacheValid =
      lastLaunchedAt != null &&
      cacheExpiresAt != null &&
      Number.isFinite(cacheExpiresAt) &&
      Date.now() < cacheExpiresAt;

    if (!cacheValid) return;

    void dispatch(restoreLocalMedia());
  }, [
    dispatch,
    hydrated,
    lastLaunchedAt,
    cacheExpiresAt,
  ]);

  return (
    <div className="app-shell">
      <InstallBanner />

      <main className="app-main">
        {children}
      </main>

      <TabBar />
    </div>
  );
}