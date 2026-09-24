import type { NextConfig } from "next";
import withPWAInit from "@ducanh2912/next-pwa";

const withPWA = withPWAInit({
  dest: "public",
  // Shell/offline only — vault bytes must not be silently cached by the SW.
  disable: process.env.NODE_ENV === "development",
  register: true,
  fallbacks: {
    document: "/offline",
  },
  workboxOptions: {
    // Do not runtime-cache /api or core tunnel responses.
    runtimeCaching: [],
  },
});

const nextConfig: NextConfig = {
  reactStrictMode: true,
  allowedDevOrigins: ["172.20.10.5", "192.168.0.0/16", "10.0.0.0/8"],
  turbopack: {},
};

export default withPWA(nextConfig);
