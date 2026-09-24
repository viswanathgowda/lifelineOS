"use client";

import { useEffect, useMemo, useState } from "react";
import { formatBytes, isImage, isPdf, isVideo } from "@/lib/media";
import { useVaultStore } from "@/lib/store";
import type { VaultFile } from "@/lib/types";

type Props = {
  files: VaultFile[];
  index: number;
  onClose: () => void;
  onChangeIndex: (index: number) => void;
};

export function PhotoViewer({ files, index, onClose, onChangeIndex }: Props) {
  const file = files[index];
  const ensureBlobUrl = useVaultStore((s) => s.ensureBlobUrl);
  const [src, setSrc] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [touchStartX, setTouchStartX] = useState<number | null>(null);

  const meta = useMemo(() => {
    if (!file) return "";
    return `${file.displayName} · ${formatBytes(file.sizeBytes)} · ${file.volumeId || "primary"}`;
  }, [file]);

  useEffect(() => {
    if (!file) return;
    let cancelled = false;
    setSrc(null);
    setError(null);
    ensureBlobUrl(file)
      .then((url) => {
        if (!cancelled) setSrc(url);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      });
    return () => {
      cancelled = true;
    };
  }, [ensureBlobUrl, file]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "ArrowRight" && index < files.length - 1) onChangeIndex(index + 1);
      if (e.key === "ArrowLeft" && index > 0) onChangeIndex(index - 1);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [files.length, index, onChangeIndex, onClose]);

  if (!file) return null;

  const download = () => {
    if (!src) return;
    const a = document.createElement("a");
    a.href = src;
    a.download = file.displayName || file.id;
    a.click();
  };

  return (
    <div
      className="viewer"
      role="dialog"
      aria-modal="true"
      aria-label={file.displayName}
      onTouchStart={(e) => setTouchStartX(e.changedTouches[0]?.clientX ?? null)}
      onTouchEnd={(e) => {
        if (touchStartX == null) return;
        const dx = (e.changedTouches[0]?.clientX ?? touchStartX) - touchStartX;
        if (dx < -50 && index < files.length - 1) onChangeIndex(index + 1);
        if (dx > 50 && index > 0) onChangeIndex(index - 1);
        setTouchStartX(null);
      }}
    >
      <header className="viewer-bar">
        <button type="button" className="viewer-btn" onClick={onClose}>
          Close
        </button>
        <p className="viewer-title">{file.displayName}</p>
        <button type="button" className="viewer-btn" onClick={download} disabled={!src}>
          Download
        </button>
      </header>

      <div className="viewer-stage">
        {error ? (
          <p className="viewer-error">{error}</p>
        ) : !src ? (
          <div className="viewer-loading">Loading…</div>
        ) : isImage(file) ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={src} alt={file.displayName} />
        ) : isVideo(file) ? (
          <video src={src} controls playsInline autoPlay />
        ) : isPdf(file) ? (
          <iframe src={src} title={file.displayName} />
        ) : (
          <iframe src={src} title={file.displayName} />
        )}
      </div>

      <footer className="viewer-footer">
        <p>{meta}</p>
        <p>
          {index + 1} / {files.length}
        </p>
      </footer>
    </div>
  );
}
