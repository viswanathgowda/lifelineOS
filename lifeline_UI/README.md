# lifeline_UI

Tunnel-only UI clients for **lifelineOS**. This folder is intentionally **outside** `lifeline_Java/` — the UI never touches the vault disk or H2 database. All access goes through authenticated APIs (Bearer token / MFA session / mTLS).

## Layout

```
lifeline_UI/
├── README.md
└── web/                 # Lightweight web client (MVP)
    ├── index.html
    ├── styles.css
    ├── app.js
    └── package.json     # optional static file server
```

## Run (web client)

1. Start the OS core (from `lifeline_Java/`):

```powershell
.\mvnw.cmd spring-boot:run
# memory card storage:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=card"
# mTLS:
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mtls"
```

2. Serve the UI (separate process):

```powershell
cd lifeline_UI\web
npx --yes serve -l 5173
```

Or open `index.html` directly in a browser for basic HTTP token mode (CORS may require the OS core to allow origins — MVP UI targets same-machine with fetch to `http://localhost:8080`).

3. In the UI, set:
   - **API base**: `http://localhost:8080` (or `https://localhost:8443` for mTLS)
   - **Token**: `lifeline-dev-token-change-me` (or MFA session token)

## Security model

| Mode | How UI authenticates |
|------|----------------------|
| Dev HTTP | `Authorization: Bearer <api-token>` |
| MFA | Verify TOTP → use returned `sessionToken` as Bearer |
| mTLS | Browser/native clients present client certificate; enroll via `/api/devices/enroll` |

The UI must **never** receive filesystem paths — only opaque file IDs from `/api/files`.

### Vault gallery

Open the web UI and click **Refresh vault** (it also loads on page open). The client:

1. Lists metadata via `GET /api/files`
2. Fetches each image via `GET /api/files/{id}/content`
3. Shows thumbnails and a larger preview — all through the tunnel

### Modes & security

See the repo root [`GUIDE.md`](../GUIDE.md) for developer vs bank-level vs production runbooks.

- `lifeline_UI/mobile/` — cert-pinned mobile app
- `lifeline_UI/shell/` — CLI tunnel client
