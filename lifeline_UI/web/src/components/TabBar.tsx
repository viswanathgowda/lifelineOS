"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const tabs = [
  { href: "/", label: "Photos", icon: PhotosIcon },
  { href: "/settings", label: "Settings", icon: SettingsIcon },
] as const;

export function TabBar() {
  const pathname = usePathname();

  return (
    <nav className="tab-bar" aria-label="Primary">
      {tabs.map(({ href, label, icon: Icon }) => {
        const active =
          href === "/"
            ? pathname === "/"
            : pathname.startsWith(href);
        return (
          <Link
            key={href}
            href={href}
            className={`tab-item ${active ? "active" : ""}`}
            aria-current={active ? "page" : undefined}
          >
            <Icon active={active} />
            <span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}

function PhotosIcon({ active }: { active: boolean }) {
  return (
    <svg width="26" height="26" viewBox="0 0 26 26" fill="none" aria-hidden>
      <rect
        x="3.5"
        y="5.5"
        width="19"
        height="15"
        rx="2.5"
        stroke={active ? "currentColor" : "#8e8e93"}
        strokeWidth="1.6"
      />
      <circle
        cx="9.2"
        cy="11"
        r="1.7"
        fill={active ? "currentColor" : "#8e8e93"}
      />
      <path
        d="M5.5 17.5l4.2-4.1 2.6 2.5 3.3-3.6 4.9 5.2"
        stroke={active ? "currentColor" : "#8e8e93"}
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function SettingsIcon({ active }: { active: boolean }) {
  return (
    <svg width="26" height="26" viewBox="0 0 26 26" fill="none" aria-hidden>
      <circle
        cx="13"
        cy="13"
        r="3.2"
        stroke={active ? "currentColor" : "#8e8e93"}
        strokeWidth="1.6"
      />
      <path
        d="M13 3.5v2.2M13 20.3v2.2M3.5 13h2.2M20.3 13h2.2M6.4 6.4l1.6 1.6M18 18l1.6 1.6M19.6 6.4l-1.6 1.6M8 18l-1.6 1.6"
        stroke={active ? "currentColor" : "#8e8e93"}
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}
