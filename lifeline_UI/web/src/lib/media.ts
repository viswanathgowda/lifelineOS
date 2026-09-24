import type { VaultFile } from "./types";

export function isImage(file: Pick<VaultFile, "contentType" | "displayName">) {
  if (file.contentType?.startsWith("image/")) return true;
  return /\.(png|jpe?g|gif|webp|bmp|heic|heif|svg)$/i.test(file.displayName || "");
}

export function isVideo(file: Pick<VaultFile, "contentType" | "displayName">) {
  if (file.contentType?.startsWith("video/")) return true;
  return /\.(mp4|mov|m4v|webm|avi|mkv)$/i.test(file.displayName || "");
}

export function isPdf(file: Pick<VaultFile, "contentType" | "displayName">) {
  if (file.contentType?.includes("pdf")) return true;
  return /\.pdf$/i.test(file.displayName || "");
}

export function formatBytes(n?: number | null) {
  const value = n ?? 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatDayLabel(iso?: string | null) {
  if (!iso) return "Unknown date";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "Unknown date";
  return date.toLocaleDateString(undefined, {
    weekday: "long",
    month: "long",
    day: "numeric",
    year: "numeric",
  });
}
