# Mockoon environment

`offline-notes.json` is a starter [Mockoon](https://mockoon.com) environment implementing the
app's wire contract (`POST /push`, `GET /pull`, `GET /health`) with static/rule-based responses —
this repo doesn't own backend semantics, so this is scripted mock data, not a real server.

## Run it

**CLI (no install required):**

```bash
npx @mockoon/cli start --data mockoon/offline-notes.json --port 8080
```

**Desktop app:** File → Open environment → select `offline-notes.json`, then press ▶.

Either way, point the app's debug panel ("Use HTTP transport" → base URL) at
`http://10.0.2.2:8080/` from an emulator, or your machine's LAN IP from a physical device.

## Scripting a scenario

Each route has multiple example responses (Applied / Conflict / Rejected / 401 / 429 / 410),
only one enabled (`"default": true`) at a time. In the desktop app, click a response to make it
active, or add your own [response rules](https://mockoon.com/docs/latest/rules/overview/) keyed
off the request body (e.g. match on `noteId` to conflict one specific note and apply everything
else) — this is the Mockoon-side equivalent of the old debug panel's per-note failure injection.
