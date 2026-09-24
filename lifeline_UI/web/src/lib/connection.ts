/**
 * Tunnel endpoint suggestions. The UI shell may be on :5173, but vault
 * bytes must come from the authenticated lifelineOS core (:8080 / :8443).
 */
export function suggestedApiBase(): string {
  if (typeof window === "undefined") return "http://localhost:8080";
  const host = window.location.hostname;
  if (!host || host === "localhost" || host === "127.0.0.1") {
    return "http://localhost:8080";
  }
  const protocol = window.location.protocol === "https:" ? "https" : "http";
  // Dev HTTP core; bank-level users switch to https://host:8443 in Settings
  return `${protocol}://${host}:8080`;
}

export function isLoopbackHost(hostname: string): boolean {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

export function isLoopbackApiBase(apiBase: string): boolean {
  try {
    return isLoopbackHost(new URL(apiBase).hostname);
  } catch {
    return false;
  }
}

function normalizeBase(value: string): string {
  return value.replace(/\/$/, "");
}

/**
 * Never point the vault client at the Next.js UI port or the removed /core proxy.
 */
export function resolveApiBase(saved: string): string {
  const suggested = suggestedApiBase();
  if (!saved) return suggested;

  const normalized = normalizeBase(saved);

  if (normalized === "/core" || normalized.endsWith("/core")) return suggested;

  try {
    const url = new URL(normalized);
    if (url.port === "5173") return suggested;
  } catch {
    return suggested;
  }

  if (
    typeof window !== "undefined" &&
    isLoopbackApiBase(normalized) &&
    !isLoopbackHost(window.location.hostname)
  ) {
    return suggested;
  }

  return normalized;
}
