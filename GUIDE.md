# lifelineOS — Complete guide (modes, security, flow)

This is the **one place** to understand how lifelineOS is meant to run: developer mode vs local hardware vs bank-level vs production, what MFA/mTLS actually do, where files live, and how data moves from UI → tunnel → vault.

For folder maps, see also [`STRUCTURE.md`](STRUCTURE.md).

---

## 1. Big picture (what you are building)

**lifelineOS** is a personal operating system core:

- Runs on **hardware you own** (today: your PC; later: a dedicated Linux device).
- Keeps **life data** (files, health, finance, …) **locally**.
- Lets UI apps talk **only through a secure tunnel** — never by opening the vault folder or database directly.

```mermaid
flowchart LR
  UI[lifeline_UI web / future apps]
  TUN[Tunnel gateway]
  AUTH[Auth: token / mTLS / MFA]
  API[Files · storage · backup · life APIs]
  VAULT[Encrypted vault on disk]
  DB[(H2 metadata)]

  UI -->|HTTPS or HTTP+Bearer| TUN
  TUN --> AUTH
  AUTH --> API
  API --> VAULT
  API --> DB
```

| Piece | Role |
|-------|------|
| `lifeline_Java` | The **OS core** — security, vault, APIs |
| `lifeline_UI` | **Clients only** — display/upload; no disk access |
| Disk (`~/.lifeline` or `D:\lifeline` or `/var/lib/lifeline`) | Where encrypted bytes actually live |

---

## 2. Security vocabulary (plain language)

### Bearer token

A secret string the client sends in a header:

```http
Authorization: Bearer lifeline-dev-token-change-me
```

**What it proves:** “Whoever knows this shared secret may call the API.”

**What it does *not* prove:** That the client is a specific enrolled device, or that the channel cannot be eavesdropped (that needs HTTPS).

Use Bearer for **developer mode** and as a **bootstrap** to enroll devices / set up MFA. It is **not** bank-level by itself.

### HTTPS / TLS

Encrypts traffic between UI and OS core so passwords, tokens, and file bytes are not readable on the wire.

lifelineOS targets **TLS 1.3** in hardened profiles.

### mTLS (mutual TLS)

Normal HTTPS: **only the server** proves its identity (certificate).

**mTLS:** both sides prove identity with certificates:

1. Server proves “I am lifelineOS.”
2. Client proves “I am this enrolled device / UI.”

The core fingerprints the client certificate (SHA-256) and maps it to a **device identity** (`/api/devices/enroll`).

This is closer to how banks and enterprise VPNs bind apps to devices.

### MFA (multi-factor authentication)

Something you **know** (token/session) + something you **have** (authenticator app TOTP code).

Flow:

1. `POST /api/auth/mfa/setup` → secret + `otpauth://` URI (scan in Google Authenticator / Authy / etc.).
2. `POST /api/auth/mfa/enable` with a 6-digit code.
3. Set `lifeline.mfa.enabled=true`.
4. Later: `POST /api/auth/mfa/verify` → short-lived **session token** used as Bearer.

### Encryption at rest

Even if someone copies the vault folder, files are **AES-256-GCM** ciphertext (`.enc`). Metadata lives in H2. The UI never sees raw paths — only opaque UUIDs.

### Audit log

Local table `security_audit_log` records auth/file events. No third-party life-data telemetry.

---

## 3. The four operating modes

Think of these as **security + storage postures**, selected with Spring profiles and properties.

| Mode | Who it’s for | Transport | Identity | MFA | Storage default |
|------|--------------|-----------|----------|-----|-----------------|
| **Developer** | Coding / UI demos on your PC | HTTP `:8080` | Shared Bearer token | Off | `%USERPROFILE%\.lifeline\data` |
| **Local (card)** | Real data on your **D:** memory card | Still HTTP unless combined | Bearer (or add mTLS) | Optional | `D:\lifeline\data` (+ `ext`) |
| **Bank-level (local hardened)** | Security testing on your machine | HTTPS `:8443` TLS 1.3 + **mTLS** | Device certificate (+ optional Bearer bootstrap) | **On** | Home or `card` + mTLS |
| **Production (device)** | Dedicated Linux appliance | HTTPS + mTLS required | Enrolled device certs | **On** | `/var/lib/lifeline` + `/media/lifeline/card` |

Bank-level ≠ “production hardware.”  
**Bank-level** = security controls (TLS + mTLS + MFA + encryption at rest).  
**Production** = those controls **on the real device image / Linux host**.

---

## 4. How to run each mode

Prerequisites for all modes:

- Java **21**
- From repo: `lifeline_Java/` for the core, `lifeline_UI/web/` for the UI

### A. Developer mode (what you used for the first upload)

**Goal:** Fast iteration. Weak auth by design.

**Terminal 1 — OS core**

```powershell
cd C:\workspace\lifelineOS\lifelineOS\lifeline_Java
.\mvnw.cmd spring-boot:run
```

**Terminal 2 — UI**

```powershell
cd C:\workspace\lifelineOS\lifelineOS\lifeline_UI\web
npm start
```

Open the UI URL (usually `http://localhost:5173`), keep:

- API base: `http://localhost:8080`
- Token: `lifeline-dev-token-change-me`

**Where uploads go**

```text
C:\Users\<you>\.lifeline\data\
  vault\xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.enc   ← encrypted file bytes
  db\lifeline.mv.db                                ← names, sizes, UUIDs
  backup\                                          ← encrypted exports
```

The UI shows images by calling `GET /api/files` then `GET /api/files/{id}/content`. The core **decrypts vault bytes in memory** and returns them for display — the browser never opens the `.enc` file on disk.

#### Critical: decrypted on the wire in developer mode?

**Yes — if you use plain HTTP, anyone on that network path can steal the image bytes.**

Two different protections:

| Protection | What it protects | What it does *not* protect |
|------------|------------------|----------------------------|
| **Encryption at rest** (AES-GCM `.enc` on disk) | Someone who steals the SD card / copies `vault\` | Data **while it is being sent** to the phone/browser |
| **TLS / mTLS on the tunnel** | Someone sniffing Wi‑Fi / LAN between phone and device | A stolen unlocked phone that already has a valid session |

So:

- Developer mode (`http://localhost:8080`) → content API returns **plaintext over HTTP**. Fine only on your own machine for coding.
- Bank-level / Raspberry Pi product → **must** use `https://…` with TLS 1.3 (+ mTLS + MFA). Then middleboxes see ciphertext only; only the enrolled client decrypts TLS.

The core *must* decrypt to show you the photo — that is normal. Banking apps also decrypt account data **inside** the TLS session. The rule is: **never serve vault content over cleartext HTTP outside a locked-down lab.**

---

### B. Local mode on the 100GB card (drive D:)

**Goal:** Persist life data on removable storage while still developing on Windows.

1. Insert the card so Windows assigns **D:**
2. Create folders (once):

```powershell
New-Item -ItemType Directory -Force -Path D:\lifeline\data, D:\lifeline\ext | Out-Null
```

3. Run with the **`card`** profile:

```powershell
cd lifeline_Java
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=card"
```

**Where uploads go**

```text
D:\lifeline\data\vault\*.enc      ← volumeId = primary
D:\lifeline\ext\vault\*.enc       ← volumeId = memory-card
D:\lifeline\data\db\              ← metadata always on primary
D:\lifeline\data\backup\          ← .lifelinebak exports
```

In the UI, set **Volume id** to `memory-card` to store blobs on the card’s `ext` tree.

> Security note: `card` alone still uses HTTP + Bearer. Combine with bank-level below for hardened local use.

---

### C. Bank-level mode (local hardened)

**Goal:** Practice bank-grade **tunnel** security on your PC: TLS 1.3 + mutual certificates + MFA.

#### Step 1 — Generate certificates (once)

```powershell
cd lifeline_Java
.\scripts\generate-mtls-certs.ps1
```

Creates:

| File | Used by |
|------|---------|
| `src/main/resources/certs/server-keystore.p12` | OS core (server identity) |
| `src/main/resources/certs/truststore.p12` | OS core (which client certs to trust) |
| `certs-dev/client-keystore.p12` | UI / curl as the client |
| `certs-dev/client-truststore.p12` | Client trusts the server |

Password default: `changeit` (change for anything real).

#### Step 2 — Run core with `mtls` (optionally + `card`)

```powershell
# Home disk + mTLS
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mtls"

# Memory card + mTLS
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=card,mtls"
```

Core listens on **`https://localhost:8443`** with **client certificate required**.

#### Step 3 — Enable MFA

1. With a bootstrap Bearer token (still needed once to call setup), call:

   - `POST /api/auth/mfa/setup`
   - Scan `otpAuthUri` in an authenticator app
   - `POST /api/auth/mfa/enable` with `{ "code": "123456" }`

2. Set in `application.properties` (or a profile file):

```properties
lifeline.mfa.enabled=true
```

3. Restart. Data APIs then expect an MFA **session token** (`POST /api/auth/mfa/verify`).

#### Step 4 — Enroll the device certificate

While presenting the **client cert**, call:

```text
POST /api/devices/enroll?clientId=ui-laptop-1
Authorization: Bearer <bootstrap-or-session>
```

After enrollment, the cert fingerprint is a first-class identity (`deviceBound`).

#### Step 5 — Call APIs with the client cert

Example with curl (PowerShell):

```powershell
curl.exe --cert-type P12 --cert .\certs-dev\client-keystore.p12:changeit `
  --cacert .\certs-dev\server.cer `
  https://localhost:8443/api/tunnel/status
```

> Browser UIs need native cert support or a small local proxy; the current web UI is primarily for **developer / local HTTP**. Bank-level browser UX is a follow-up (or use curl / a desktop client with the P12).

**Bank-level checklist**

- [x] Encryption at rest (always in current builds)
- [ ] HTTPS TLS 1.3 (`mtls` / `device` profiles)
- [ ] mTLS client cert + enrollment
- [ ] MFA enabled + session tokens
- [ ] Strong unique `lifeline.storage.encryption-key` and API secrets
- [ ] No default `changeit` / `lifeline-dev-token-change-me` in real use

---

### D. Production / device mode (Linux appliance path)

**Goal:** Same Java core, paths and policies for dedicated hardware.

```bash
cd lifeline_Java
./mvnw spring-boot:run -Dspring-boot.run.profiles=device
```

From `application-device.properties`:

| Setting | Production intent |
|---------|-------------------|
| Storage | `/var/lib/lifeline/data` |
| Removable card | `/media/lifeline/card` |
| Port | `8443` HTTPS |
| mTLS | `client-auth=need` |
| MFA | `enabled=true` |
| Device identity | `require-device-identity=true` |

Your 100GB card on Linux would be mounted (example) at `/media/lifeline/card`, then used as volume `memory-card`.

Bootable `lifeline_image/` (minimal Linux + Java as a system service) is the future packaging step — same security model.

---

## 5. Complete project flow (end-to-end)

### 5.1 Upload an image (developer mode)

```mermaid
sequenceDiagram
  participant U as UI browser
  participant C as lifeline_Java
  participant V as vault/*.enc
  participant D as H2 db

  U->>C: POST /api/files (multipart) + Bearer
  C->>C: Authenticate token + scope FILES_WRITE
  C->>C: AES-GCM encrypt bytes
  C->>V: Write uuid.enc
  C->>D: Save metadata (id, name, type, size, volumeId)
  C-->>U: JSON metadata (opaque id, no path)
```

### 5.2 View all images again in the UI

1. UI calls `GET /api/files` → list of metadata.
2. For each image, UI calls `GET /api/files/{id}/content`.
3. Core decrypts vault bytes in memory and returns them.
4. Browser shows a thumbnail / preview via a temporary blob URL.

**Important:** Viewing does not mean the UI reads `C:\Users\…\vault\…enc`. Only the Java core can decrypt.

### 5.3 Backup

1. `POST /api/backup/export`
2. Core zips decrypted file payloads + manifest, then **re-encrypts** the zip → `.lifelinebak` under `…/backup/`.
3. Restore with `POST /api/backup/import` (multipart).

### 5.4 Bank-level access flow

```mermaid
sequenceDiagram
  participant U as Client (cert)
  participant C as OS core
  participant A as Authenticator app

  U->>C: TLS handshake (mTLS)
  C->>C: Fingerprint client cert → device identity
  U->>C: POST /api/auth/mfa/verify + TOTP
  A-->>U: 6-digit code
  C-->>U: sessionToken
  U->>C: API calls with Bearer sessionToken (+ cert)
  C->>C: Scopes + audit log
```

---

## 6. Tech stack (what each layer uses)

| Layer | Technology | Why |
|-------|------------|-----|
| Language | Java 21 | Long-lived system services, strong crypto APIs |
| Framework | Spring Boot 4 (Web MVC) | HTTP/HTTPS APIs for the tunnel surface |
| Security | Spring Security | Stateless filters: cert → token → MFA session |
| DB | H2 file mode | Embedded metadata on local/removable disk (MVP) |
| Vault crypto | AES-256-GCM | Authenticated encryption at rest |
| MFA | TOTP (RFC 6238, HMAC-SHA1) | Standard authenticator-app second factor |
| mTLS | JVM keystores (PKCS12) + Tomcat SSL | Mutual device identity |
| UI | Static web (`lifeline_UI/web`) | Tunnel-only client; gallery via content API |
| Build | Maven Wrapper | Reproducible builds without global Maven |

---

## 7. API cheat sheet

Public (no auth):

- `GET /` — core status (includes storage root path)
- `GET /api/tunnel/status` — tunnel / mTLS / MFA flags

Authenticated:

| Area | Paths |
|------|--------|
| Files | `GET/POST /api/files`, `GET …/{id}/content`, `DELETE …/{id}` |
| Storage | `GET /api/storage/volumes`, `POST …/mount` |
| Backup | `POST /api/backup/export`, `POST /api/backup/import` |
| MFA | `/api/auth/mfa/status\|setup\|enable\|verify` |
| Devices | `POST /api/devices/enroll`, `GET /api/devices/me` |
| Life data | `/api/health`, `/api/finance`, `/api/insights/summary` |

Always send:

```http
Authorization: Bearer <api-token-or-mfa-session>
```

---

## 8. “Why did we use Bearer if we want bank security?”

Because products are built in layers:

1. **Make the vault + APIs work** (developer mode, Bearer).
2. **Move data to real media** (`card` profile).
3. **Turn on bank-grade tunnel** (`mtls` + MFA).
4. **Freeze that posture on a device** (`device` / future image).

Bearer is the scaffolding. **Bank-level is mTLS + MFA + TLS 1.3 + encryption at rest + enrolled secrets.**  
If you skip to step 3 too early, you cannot open a simple browser UI without client certificates — which is why demos start on HTTP.

---

## 9. Recommended path (product order)

1. **Lab on PC** — gallery + vault under `~\.lifeline\data` (HTTP ok *only* here).
2. **Removable media** — Windows `card` profile or Pi SD/USB mounts; confirm blobs on the card.
3. **Bank-grade tunnel** — certs + `mtls` + MFA; **no cleartext content on the network**.
4. **Raspberry Pi device** — profile `device`; enroll phone; mobile UI over mTLS.
5. **Hardened image** — `lifeline_image/` (future): auto-start core at boot, firewall only tunnel port.

---

## 10. Related docs

| Doc | Contents |
|-----|----------|
| [`README.md`](README.md) | Product vision + quick start |
| [`STRUCTURE.md`](STRUCTURE.md) | Folder / package map in detail |
| [`lifeline_Java/README.md`](lifeline_Java/README.md) | Core APIs + config |
| [`lifeline_UI/README.md`](lifeline_UI/README.md) | UI-only client notes |

---

*lifelineOS — your life, your hardware, your tunnel.*
