# lifeline_Java — lifelineOS core (Java)

This directory is **not a standalone “backend app.”** It is the **Java implementation of lifelineOS** — the operating system’s core system layer: secure tunnels, authentication, file management, local vault storage, and life-data services.

The [parent README](../README.md) describes the full OS product. **Here is where the OS is developed in Java.**

---

## Java and “building an OS”

**Yes, we implement lifelineOS in Java** — with a clear split that keeps the project achievable and secure:

| What | How |
|------|-----|
| **Kernel / drivers / boot** | Minimal **hardened Linux** (MVP: dev PC; later: appliance image). Handles hardware, block devices, networking at the metal. |
| **lifelineOS (what users experience)** | **Java 21** in this repo — tunnels, auth, file manager logic, encrypted vault, APIs, life domains. |
| **UI** | Separate clients; **only** the secure tunnel, never direct disk or DB access. |

A from-scratch **kernel written entirely in Java** (e.g. old JavaOS-style stacks) is **out of scope** — slow to mature and hard to harden. A **Java-first OS product** — system services that *are* the OS — is standard for secure appliances and matches our goals.

**Mental model:** Linux boots the box; **Java runs lifelineOS.**

```mermaid
flowchart TB
    subgraph ui [UI layer — not this repo]
        C[Clients]
    end

    subgraph java_os [lifeline_Java — lifelineOS core]
        TUN[Tunnel gateway]
        AUTH[AuthN / AuthZ]
        FILES[File management]
        VAULT[Encrypted vault & metadata]
        DOM[Life domains — health, finance, …]
    end

    subgraph base [OS base — future image / MVP host OS]
        L[Linux]
        D[(Disks — local MVP, extendable later)]
    end

    C -->|bank-grade+ tunnel| TUN
    TUN --> AUTH
    AUTH --> FILES
    AUTH --> VAULT
    AUTH --> DOM
    FILES --> D
    VAULT --> D
    DOM --> D
    L --> D
    L --> java_os
```

---

## What lifelineOS provides (system capabilities)

These are **OS-level services**, not optional app features:

| Capability | Description |
|------------|-------------|
| **Secure tunnels** | All UI ↔ system traffic over encrypted, mutually authenticated channels (TLS 1.3+, **mTLS** target). No plain HTTP in production. Zero trust on LAN. |
| **Authentication** | Identity and session model stricter than typical banking: hardware-bound keys where possible, MFA, short-lived credentials, least privilege. |
| **File management** | Basic operations on the user vault: list, organize, upload/download through the API, quotas, encrypted at rest — **not** exposing SMB/NFS or raw paths to the network. |
| **Local storage** | **MVP:** embedded DB + vault files on the **local computer**. **Later:** OS-managed internal and **extendable** volumes on device. |
| **Life data** | Domains (health, fitness, finance, insights) built on the same vault and security boundary. |

---

## MVP vs lifelineOS on hardware

| Phase | Host | Storage | What you run |
|-------|------|---------|----------------|
| **MVP** | Your PC (Windows / macOS / Linux) | `~/.lifeline/data` (or similar) on **local disk** | `lifeline_Java` process = **OS core in development** |
| **Product** | Dedicated hardware + **lifelineOS image** | Internal + **extendable** encrypted storage | Same Java core as **system service** at boot |

MVP lets you implement tunnels, auth, and file APIs before custom hardware exists. The security and storage contracts stay the same when the core moves onto the appliance.

---

## 1. Secure tunnels (non-negotiable)

Every client (web, mobile, future shell) connects **only** through the tunnel gateway implemented here.

**Target bar (above typical banking)**

- Mutual TLS — client and server prove identity
- TLS 1.3+ only in production
- Encryption at rest for vault and DB files
- Strong authentication (MFA / device keys when available)
- Least-privilege scopes per client
- Local security audit log (no third-party life-data telemetry)
- No anonymous or “open” system APIs on the internet

| MVP | On device |
|-----|-----------|
| HTTPS + Spring Security; localhost / trusted LAN; path to mTLS | Device certificates; optional TPM / secure element |
| Dev trust stores documented | OS-managed tunnel and firewall policy |

---

## 2. File management (OS primitive)

File management is a **first-class OS service**, not a separate utility app.

**Planned responsibilities (`lifelineOS/files` or similar)**

- Vault namespaces (e.g. documents, health exports, backups)
- List / metadata / move / delete through **authenticated APIs only**
- Enforced encryption at rest and per-path permissions
- Integration with extendable storage (mount external volume → vault subtree)
- No direct filesystem paths leaked to UI — opaque IDs and scoped tokens

UI requests files **through the tunnel**; the Java core enforces policy and talks to disk.

---

## 3. Local storage (MVP)

Until hardware images exist, persistence stays on the **developer / user PC**:

| Aspect | MVP |
|--------|-----|
| Metadata / structured data | Embedded SQL (**SQLite** or **H2** file mode) |
| Vault files | Encrypted files under configurable root, e.g. `${user.home}/.lifeline/data` |
| Access | **Only** lifelineOS Java core; UI via tunnel APIs |
| Backup | User-controlled encrypted export |

```properties
# Planned
lifeline.storage.path=${user.home}/.lifeline/data
lifeline.storage.encryption.enabled=true
```

---

## Technology stack (MVP core)

| Item | Choice |
|------|--------|
| Language | **Java 21** |
| Framework | **Spring Boot 4** (system service host — Web MVC for tunnel APIs) |
| Build | **Maven** (`mvnw` / `mvnw.cmd`) |
| Tests | JUnit 5 + MockMvc |
| Persistence | **H2** file DB under `~/.lifeline/data/db` + encrypted vault files |
| Security | Spring Security, Bearer token (MVP), HTTPS/mTLS hooks, local audit log |
| Encryption | AES-256-GCM at rest for vault payloads |

Artifact: `com.lifeline:lifelineOS` (`0.0.1-SNAPSHOT`).

---

## Internal architecture (Java packages)

```
lifelineOS/
├── security/          # Bearer auth, device identity, audit log
├── tunnel/            # Gateway status, mTLS cert filter, client registry
├── mfa/               # TOTP + MFA sessions
├── files/             # File management APIs (opaque IDs, namespaces, volumeId)
├── persistence/       # Encrypted vault blobs + crypto
├── storage/           # Primary + extendable volumes (D: / Linux mounts)
├── backup/            # Encrypted backup export/import
├── config/            # lifeline.* properties, CORS
├── common/            # API errors
├── health/
├── finance/
├── insights/
├── HomeController.java
└── LifelineOsApplication.java
```

**Layers:** Security/tunnel → API → Application (use cases) → Domain → Infrastructure (disk, DB, encryption).

---

## Project layout

```
lifeline_Java/
├── pom.xml
├── mvnw, mvnw.cmd
├── src/main/java/lifelineOS/
├── src/main/resources/application.properties
├── src/test/java/lifelineOS/
└── README.md
```

---

## Run (MVP development)

### Prerequisites

- **Java 21** JDK on your `PATH` (`java -version` should show 21)
- Maven Wrapper is included — no separate Maven install required

### Start the OS core

From this directory (`lifeline_Java/`):

**Windows (PowerShell or Command Prompt):**

```powershell
.\mvnw.cmd spring-boot:run
```

**macOS / Linux:**

```bash
./mvnw spring-boot:run
```

First run downloads dependencies. When ready, the server listens on **port 8080**.

### Auth (required for data APIs)

Default dev token (change in `application.properties`):

```text
lifeline-dev-token-change-me
```

Send on every `/api/**` call except `/api/tunnel/status`:

```powershell
# Windows PowerShell
$headers = @{ Authorization = "Bearer lifeline-dev-token-change-me" }
Invoke-RestMethod http://localhost:8080/api/files -Headers $headers
```

```bash
curl -H "Authorization: Bearer lifeline-dev-token-change-me" http://localhost:8080/api/files
```

### API surface (MVP)

| Method | Path | Auth | Notes |
|--------|------|------|--------|
| `GET` | `/` | no | Core status JSON |
| `GET` | `/api/tunnel/status` | no | Tunnel gateway status |
| `POST` | `/api/devices/enroll` | yes + client cert | Bind mTLS cert fingerprint to device |
| `GET` | `/api/devices/me` | yes | Current principal / device flags |
| `GET`/`POST` | `/api/auth/mfa/*` | yes | TOTP setup, enable, verify → session |
| `GET` | `/api/files?namespace=` | yes | List vault file metadata (opaque IDs) |
| `GET` | `/api/files/{id}` | yes | Metadata only — no disk paths |
| `GET` | `/api/files/{id}/content` | yes | Download decrypted bytes |
| `POST` | `/api/files` | yes | Multipart upload (`file`, `namespace`, `volumeId`) |
| `PUT` | `/api/files/{id}` | yes | Move/rename (`namespace`, `displayName`) |
| `DELETE` | `/api/files/{id}` | yes | Delete metadata + encrypted blob |
| `GET` | `/api/storage/volumes` | yes | Primary + extendable volume status |
| `POST` | `/api/storage/volumes/mount` | yes | Mount path as volume id |
| `POST` | `/api/backup/export` | yes | Encrypted `.lifelinebak` on disk |
| `POST` | `/api/backup/import` | yes | Restore encrypted archive |
| `GET`/`POST` | `/api/health` | yes | Health domain records |
| `GET`/`POST` | `/api/finance` | yes | Finance domain entries |
| `GET` | `/api/insights/summary` | yes | Cross-domain counts stub |

Namespaces: `DOCUMENTS`, `HEALTH_EXPORTS`, `BACKUPS`, `FINANCE`, `GENERAL`.

Upload example:

```powershell
curl.exe -H "Authorization: Bearer lifeline-dev-token-change-me" `
  -F "file=@.\note.txt" -F "namespace=DOCUMENTS" `
  http://localhost:8080/api/files
```

### Verify

| Endpoint | Notes |
|----------|--------|
| `GET /` | Status JSON includes `status: Lifeline OS Running` |
| Default port | `8080` — **dev only**; production requires HTTPS/tunnel |

```powershell
# Windows PowerShell
Invoke-RestMethod http://localhost:8080/
```

```bash
# macOS / Linux
curl http://localhost:8080/
```

### Build and test

```powershell
# Windows
.\mvnw.cmd test
.\mvnw.cmd package
```

```bash
# macOS / Linux
./mvnw test
./mvnw package
```

The packaged JAR is written to `target/`.

---

## Configuration

```properties
spring.application.name=lifelineOS
lifeline.storage.path=${user.home}/.lifeline/data
lifeline.storage.encryption.enabled=true
lifeline.storage.encryption-key=<Base64 32-byte AES key>
lifeline.security.api-token=<change-me>
lifeline.mfa.enabled=false
lifeline.tunnel.require-https=false
lifeline.tunnel.mtls-enabled=false
lifeline.tunnel.require-device-identity=false
lifeline.tunnel.allowed-protocols=TLSv1.3
```

### Profiles

| Profile | Purpose |
|---------|---------|
| `card` | Primary data on **`D:/lifeline/data`**, extendable volume `memory-card` → `D:/lifeline/ext` |
| `mtls` | HTTPS 8443, TLS 1.3, `client-auth=need` (run `scripts/generate-mtls-certs.ps1` first) |
| `device` | Linux appliance paths + mTLS + MFA |

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=card"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mtls"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=card,mtls"
```

Data layout on disk (default):

```text
~/.lifeline/data/
  db/          # H2 metadata (file entries, audit, life domains, devices, MFA)
  vault/       # AES-GCM encrypted blobs (*.enc)
  backup/      # Encrypted .lifelinebak exports
```

Card layout: see [STRUCTURE.md](../STRUCTURE.md). UI lives in [lifeline_UI](../lifeline_UI/).

---

## Roadmap

### MVP (Java OS core on PC)

- [x] Tunnel gateway + client registry + HTTPS enforcement
- [x] **mTLS termination** + device enrollment by cert fingerprint
- [x] **MFA (TOTP)** + session tokens
- [x] Local embedded DB (H2) + encrypted vault (AES-GCM)
- [x] File management APIs (opaque IDs + volume id)
- [x] **Encrypted backup/export**
- [x] **Extendable storage volumes** (D: card / Linux mount)
- [x] Local security audit log
- [ ] TPM / hardware-bound client keys
- [ ] Richer backup manifest round-trip

### lifelineOS on hardware

- [ ] Bootable image: minimal Linux + lifeline Java core as system service
- [ ] Hardware-bound certificates / secure element
- [ ] Richer life domains and cross-domain insights
- [ ] Mobile/shell UI with cert pinning

---

## Related

- [lifelineOS (parent)](../README.md)
- [STRUCTURE.md](../STRUCTURE.md) — full folder + storage map
- [lifeline_UI](../lifeline_UI/) — tunnel-only clients
- Entry: `lifelineOS.LifelineOsApplication`
