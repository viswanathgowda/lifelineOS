# lifelineOS web PWA

Next.js **shell** for the vault gallery. Vault bytes move only through the
authenticated lifelineOS **core tunnel** (never via UI port 5173).

## Flow

1. Configure tunnel API base + token (Settings)
2. Tap **Launch Vault** — authenticated pull → local cache
3. Browse offline until **TTL** (Settings) expires → cache wiped
4. Launch Vault again for new data

## Scripts

```powershell
npm install
npm run mobile   # build + start PWA on :5173
npm run dev
```
