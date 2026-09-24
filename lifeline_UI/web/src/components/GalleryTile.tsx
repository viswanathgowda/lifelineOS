"use client";

import { useEffect, useState } from "react";
import { isImage, isVideo } from "@/lib/media";
import { useVaultStore } from "@/lib/store";
import type { VaultFile } from "@/lib/types";

type Props = {
  file: VaultFile;
  onOpen: () => void;
};

export function GalleryTile({ file, onOpen }: Props) {
  const ensureBlobUrl = useVaultStore((s) => s.ensureBlobUrl);
  const cachedUrl = useVaultStore((s) => s.objectUrls[file.id]);
  const [src, setSrc] = useState<string | null>(cachedUrl ?? null);
  const [failed, setFailed] = useState(false);
  const media = isImage(file) || isVideo(file);

  useEffect(() => {
    if (cachedUrl) setSrc(cachedUrl);
  }, [cachedUrl]);

  useEffect(() => {
    if (!media || cachedUrl) return;
    let cancelled = false;
    ensureBlobUrl(file)
      .then((url) => {
        if (!cancelled) setSrc(url);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, [ensureBlobUrl, file, media, cachedUrl]);

  return (
    <button type="button" className="gallery-tile" onClick={onOpen}>
      {src && isImage(file) ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={src} alt={file.displayName} loading="lazy" />
      ) : src && isVideo(file) ? (
        <video src={src} muted playsInline preload="metadata" />
      ) : failed || !media ? (
        <div className="tile-fallback">
          <span>{(file.contentType || "FILE").split(";")[0]}</span>
        </div>
      ) : (
        <div className="tile-skeleton" />
      )}
      {isVideo(file) ? <span className="video-badge">▶</span> : null}
    </button>
  );
}
