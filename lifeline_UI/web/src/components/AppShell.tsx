"use client";

import { useEffect } from "react";
import { useVaultStore } from "@/lib/store";
import { InstallBanner } from "./InstallBanner";
import { TabBar } from "./TabBar";

export function AppShell({ children }: { children: React.ReactNode }) {
  const syncApiBaseForHost = useVaultStore((s) => s.syncApiBaseForHost);
  const hydrated = useVaultStore((s) => s.hydrated);
  const restoreLocalMedia = useVaultStore((s) => s.restoreLocalMedia);
  const enforceCachePolicy = useVaultStore((s) => s.enforceCachePolicy);
  const isCacheValid = useVaultStore((s) => s.isCacheValid);

  useEffect(() => {
    const finish = () => {
      syncApiBaseForHost();
      void enforceCachePolicy().then(() => {
        if (useVaultStore.getState().isCacheValid()) {
          void restoreLocalMedia();
        }
      });
    };

    if (useVaultStore.persist.hasHydrated()) {
      finish();
      return;
    }
    return useVaultStore.persist.onFinishHydration(finish);
  }, [syncApiBaseForHost, enforceCachePolicy, restoreLocalMedia]);

  useEffect(() => {
    if (!hydrated) return;
    syncApiBaseForHost();
  }, [hydrated, syncApiBaseForHost]);

  useEffect(() => {
    if (!hydrated || !isCacheValid()) return;
    void restoreLocalMedia();
  }, [hydrated, isCacheValid, restoreLocalMedia]);

  return (
    <div className="app-shell">
      <InstallBanner />
      <main className="app-main">{children}</main>
      <TabBar />
    </div>
  );
}
