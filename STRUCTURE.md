# STRUCTURE.md — lifelineOS repository map

This document explains **every major folder and package** in the lifelineOS repo: what it is for, how pieces talk to each other, and how storage moves from a Windows memory card (drive **D:**) to a Linux device with removable media.

For **how to run developer / local / bank-level / production modes** and plain-language security (Bearer, MFA, mTLS), start with [`GUIDE.md`](GUIDE.md).

---

## 1. Top-level repository

```
lifelineOS/                          ← git root (this product)
├── README.md                        ← product vision + quick start
├── STRUCTURE.md                     ← this file (detailed map)
├── lifeline_Java/                   ← OS core (Java 21 / Spring Boot)
├── lifeline_UI/                     ← UI clients (tunnel-only; NOT inside Java)
└── (future) lifeline_image/         ← bootable Linux + Java core as system service
```

### Why three (later) trees?

| Tree | Role | Talks to disk? |
|------|------|----------------|
| **lifeline_Java** | The OS: tunnels, auth, MFA, vault, files, life data, backup | **Yes** — only the Java core may touch vault/DB |
| **lifeline_UI** | Human interfaces (web now; mobile/shell later) | **No** — only HTTPS/mTLS APIs |
| **lifeline_image** | Appliance packaging (future) | Host OS mounts disks; Java still owns the vault |

The UI is **outside** `lifeline_Java` on purpose so a compromised browser never gets raw filesystem paths or DB credentials.

---

## 2. Architecture (runtime)

```mermaid
flowchart TB
    subgraph ui [lifeline_UI]
        WEB[web client]
    end

    subgraph java_os [lifeline_Java — OS core]
        TUN[Tunnel gateway TLS / mTLS]
        AUTH[AuthN — token / device cert / MFA session]
        FILES[File management APIs]
        VAULT[Encrypted vault]
        VOL[Volume service — primary + card]
        BAK[Encrypted backup/export]
        DOM[Life domains]
    end

    subgraph disks [Local / removable storage]
        P[Primary path]
        C[Extendable volume e.g. D: or /media]
    end

    WEB -->|Bearer or mTLS| TUN
    TUN --> AUTH
    AUTH --> FILES
    AUTH --> BAK
    AUTH --> DOM
    FILES --> VAULT
    VAULT --> VOL
    VOL --> P
    VOL --> C
    BAK --> VOL
```

---

## 3. `lifeline_Java/` — OS core

```
lifeline_Java/
├── pom.xml
├── mvnw / mvnw.cmd
├── README.md
├── scripts/
│   ├── generate-mtls-certs.ps1      ← Windows: create server/client PKCS12 stores
│   └── generate-mtls-certs.sh       ← Linux/macOS equivalent
├── certs-dev/                       ← generated client keystores (gitignored)
├── src/main/resources/
│   ├── application.properties       ← default (home directory storage)
│   ├── application-card.properties  ← Windows D: memory card layout
│   ├── application-mtls.properties  ← local TLS 1.3 + client-auth=need
│   ├── application-device.properties← Linux appliance paths + mTLS + MFA
│   └── certs/                       ← server-keystore.p12 + truststore.p12
└── src/main/java/lifelineOS/
    ├── LifelineOsApplication.java   ← entry point
    ├── HomeController.java          ← GET / status
    ├── config/                      ← lifeline.* properties, CORS
    ├── common/                      ← API error types
    ├── security/                    ← Spring Security, Bearer filter, devices, audit
    ├── tunnel/                      ← gateway status, mTLS cert filter, client registry
    ├── mfa/                         ← TOTP setup/verify + session tokens
    ├── persistence/                 ← AES-GCM crypto + vault blob I/O
    ├── storage/                     ← primary + extendable volumes
    ├── files/                       ← opaque-ID file APIs
    ├── backup/                      ← encrypted .lifelinebak export/import
    ├── health/ / finance/ / insights/
    └── …
```

### Package responsibilities

| Package | Responsibility |
|---------|----------------|
| `config` | `LifelineProperties` (storage paths, volumes, MFA, tunnel, backup), CORS for UI |
| `security` | Filter chain, Bearer/API token, device enrollment, audit log entities |
| `tunnel` | Public gateway status; **ClientCertificateAuthFilter** binds X.509 fingerprint → device |
| `mfa` | RFC 6238 TOTP; encrypted secret at rest; short-lived MFA session tokens |
| `persistence` | `VaultCryptoService` (AES-256-GCM), `VaultStorageService` (blob store per volume) |
| `storage` | `VolumeService` mounts primary + extendable roots (D: card, Linux `/media/…`) |
| `files` | List/upload/download/move/delete; returns **UUID + volumeId**, never absolute paths |
| `backup` | Zip vault contents → encrypt → write `.lifelinebak`; import restores into `BACKUPS` |
| `health` / `finance` / `insights` | Life-domain APIs on the same auth boundary |

### Profiles (how you run)

| Profile | Command idea | Storage | Security |
|---------|--------------|---------|----------|
| *(default)* | `.\mvnw.cmd spring-boot:run` | `%USERPROFILE%\.lifeline\data` | HTTP + Bearer token |
| `card` | `… -Dspring-boot.run.profiles=card` | **`D:/lifeline/data`** + volume `memory-card` → `D:/lifeline/ext` | Same as default (MVP on PC) |
| `mtls` | `… profiles=mtls` | default or combined | HTTPS **8443**, TLS 1.3, **client cert required** |
| `device` | on Linux appliance | `/var/lib/lifeline/data` + `/media/lifeline/card` | mTLS + MFA on |

Combine profiles when needed, e.g. `card,mtls`.

---

## 4. Storage layout on disk

### Windows MVP with 100GB memory card (drive D:)

Activate profile **`card`**. Expected layout:

```
D:\lifeline\
├── data\                      ← primary (H2 + primary vault + backups)
│   ├── db\                    ← embedded H2 files
│   ├── vault\                 ← *.enc AES-GCM blobs
│   ├── backup\                ← *.lifelinebak exports
│   └── certs\                 ← (device profile) keystores on appliance
└── ext\                       ← extendable volume id = memory-card
    └── vault\                 ← optional overflow / dedicated card vault
```

Upload with `volumeId=memory-card` to store blobs on the card’s `ext` tree while metadata stays in H2 on primary.

### Linux device (later)

Profile **`device`**:

```
/var/lib/lifeline/data/        ← primary
/media/lifeline/card/          ← removable volume (your 100GB card mounted here)
```

Same Java APIs — only paths in config change.

### Security rules for storage

1. UI never sees `D:\…` or `/media/…` — only `volumeId` strings (`primary`, `memory-card`).
2. All blob bytes are encrypted at rest (AES-GCM) before write.
3. Mount/unmount availability is exposed via `GET /api/storage/volumes` (usable/total bytes when present).

---

## 5. Security stack (bank-grade path)

| Layer | MVP implementation | Hardening path |
|-------|--------------------|----------------|
| Transport | HTTP localhost; `mtls`/`device` → TLS 1.3 | Always TLS on appliance |
| Client identity | Bearer API token | **mTLS** client cert; enroll fingerprint in `device_identity` |
| MFA | Optional TOTP (`lifeline.mfa.enabled`) | Required on `device` profile |
| Sessions | MFA issues `mfa_…` Bearer with TTL | Hardware-bound keys later |
| Audit | Local `security_audit_log` table | No third-party telemetry |
| Backup | Encrypted `.lifelinebak` under owner control | Offline air-gapped copies |

### Typical auth flows

**Dev (token only)**  
`Authorization: Bearer lifeline-dev-token-change-me`

**MFA**  
1. Token auth → `POST /api/auth/mfa/setup` → authenticator app  
2. `POST /api/auth/mfa/enable` with code  
3. Set `lifeline.mfa.enabled=true` and restart  
4. `POST /api/auth/mfa/verify` → use `sessionToken`

**mTLS device**  
1. Generate certs: `scripts/generate-mtls-certs.ps1`  
2. Run with `mtls` profile  
3. Call enroll with client cert + Bearer bootstrap: `POST /api/devices/enroll?clientId=ui-1`  
4. Subsequent calls authenticate by certificate fingerprint

---

## 6. `lifeline_UI/` — clients outside the OS core

```
lifeline_UI/
├── README.md
└── web/                 ← MVP browser UI
    ├── index.html
    ├── app.js
    ├── styles.css
    └── package.json
```

- Serves on its own port (e.g. 5173); calls OS core on 8080/8443.
- CORS is enabled on the Java core for localhost UI origins.
- Future: `mobile/`, `shell/` — still tunnel-only.

---

## 7. Key HTTP APIs (summary)

| Area | Paths |
|------|--------|
| Status | `GET /`, `GET /api/tunnel/status` |
| Devices | `POST /api/devices/enroll`, `GET /api/devices/me` |
| MFA | `/api/auth/mfa/status\|setup\|enable\|verify` |
| Files | `/api/files` (+ `volumeId` on upload) |
| Storage | `GET /api/storage/volumes`, `POST /api/storage/volumes/mount` |
| Backup | `POST /api/backup/export`, `POST /api/backup/import` |
| Life data | `/api/health`, `/api/finance`, `/api/insights/summary` |

Full tables live in `lifeline_Java/README.md`.

---

## 8. Suggested test path (your hardware)

1. Insert 100GB card as **D:**; create `D:\lifeline\data` and `D:\lifeline\ext`.
2. Run core:  
   `cd lifeline_Java` → `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=card"`
3. Open UI: `cd lifeline_UI\web` → `npm start` (or `npx serve`).
4. Upload a file with volume `memory-card`; confirm blob under `D:\lifeline\ext\vault\`.
5. `POST /api/backup/export` → encrypted file under `D:\lifeline\data\backup\`.
6. Generate mTLS certs; re-run with `card,mtls`; enroll device; verify `GET /api/devices/me` shows `deviceBound: true`.
7. Later: mount the same card on Linux at `/media/lifeline/card` and use profile `device`.

---

## 9. What is intentionally not here yet

- Full CA hierarchy / hardware TPM binding (scripts use self-signed for local mTLS)
- Rich backup manifest re-import (import restores blobs into `BACKUPS` namespace)
- Bootable `lifeline_image/`
- Mobile cert pinning

Those build on the same package boundaries documented above.
