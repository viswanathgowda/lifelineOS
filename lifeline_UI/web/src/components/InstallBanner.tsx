"use client";

import { useEffect, useMemo, useState } from "react";

const DISMISS_KEY = "lifeline-install-dismissed";

function isStandalone(): boolean {
  if (typeof window === "undefined") return false;
  const media = window.matchMedia("(display-mode: standalone)").matches;
  const ios = "standalone" in navigator && Boolean((navigator as Navigator & { standalone?: boolean }).standalone);
  return media || ios;
}

function isIos(): boolean {
  if (typeof navigator === "undefined") return false;
  return /iphone|ipad|ipod/i.test(navigator.userAgent);
}

export function InstallBanner() {
  const [visible, setVisible] = useState(false);
  const [deferred, setDeferred] = useState<Event | null>(null);
  const ios = useMemo(() => isIos(), []);

  useEffect(() => {
    if (isStandalone()) return;
    if (localStorage.getItem(DISMISS_KEY) === "1") return;

    const onPrompt = (e: Event) => {
      e.preventDefault();
      setDeferred(e);
      setVisible(true);
    };
    window.addEventListener("beforeinstallprompt", onPrompt);

    // iOS has no beforeinstallprompt — still show Add to Home Screen tip
    if (isIos()) setVisible(true);

    return () => window.removeEventListener("beforeinstallprompt", onPrompt);
  }, []);

  if (!visible) return null;

  const dismiss = () => {
    localStorage.setItem(DISMISS_KEY, "1");
    setVisible(false);
  };

  const install = async () => {
    const promptEvent = deferred as unknown as {
      prompt: () => Promise<void>;
      userChoice: Promise<{ outcome: string }>;
    } | null;
    if (!promptEvent) return;
    await promptEvent.prompt();
    await promptEvent.userChoice;
    setVisible(false);
  };

  return (
    <div className="install-banner" role="status">
      <div>
        <strong>Install lifelineOS</strong>
        <p>
          {ios
            ? "Tap Share → Add to Home Screen to keep photos like an app (same storage, faster return)."
            : "Install the app so gallery cache stays when you leave the browser."}
        </p>
      </div>
      <div className="install-actions">
        {!ios && deferred ? (
          <button type="button" onClick={() => void install()}>
            Install
          </button>
        ) : null}
        <button type="button" className="secondary" onClick={dismiss}>
          Not now
        </button>
      </div>
    </div>
  );
}
