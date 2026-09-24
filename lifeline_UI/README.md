# lifeline_UI

Tunnel-only UI clients for **lifelineOS**. This folder is intentionally **outside** `lifeline_Java/` — the UI never touches the vault disk or H2 database. All vault access goes through the **authenticated tunnel** (Bearer token / MFA session / mTLS) to the OS core.

## Layout

```
lifeline_UI/
├── README.md
└── web/                 # Next.js PWA shell (gallery + settings)
    ├── src/app/
    └── package.json
```

## Security model (web)

| Piece | Role |
|------|------|
| UI shell `:5173` | Serves the app / PWA only — **not** a vault gateway |
| Core tunnel `:8080` / `:8443` | Authenticated APIs; only path for vault bytes |
| **Launch Vault** | Explicit user action: open tunnel → pull → store locally |
| Local cache TTL | Settings-controlled; expired cache is wiped; Launch Vault required again |

The UI must **never** receive filesystem paths — only opaque file IDs from `/api/files`.

## Run (web PWA)

1. Start the OS core (from `lifeline_Java/`):

```powershell
.\mvnw.cmd spring-boot:run
```

2. Run the UI shell:

```powershell
cd lifeline_UI\web
npm install
npm run mobile       # production PWA on :5173 (phone / Add to Home Screen)
# or: npm run dev
```

3. On the phone open `http://<pc-lan-ip>:5173`, set **Tunnel API base** to `http://<pc-lan-ip>:8080` (or `https://…:8443` for bank-level), then tap **Launch Vault**.

### Modes

See [`GUIDE.md`](../GUIDE.md) for developer vs bank-level (mTLS + MFA) runbooks.
