# Mockoon environment

`offline-notes.json` is a starter [Mockoon](https://mockoon.com) environment implementing the
app's wire contract (`POST /push`, `GET /pull`, `GET /health`) with static/rule-based responses —
this repo doesn't own backend semantics, so this is scripted mock data, not a real server.

The contract these responses have to satisfy is specified in
[`openapi/offline-notes.yaml`](../openapi/offline-notes.yaml), including the rules the schemas
can't express: per-op independence, `opId` idempotency, results versus HTTP failures, and the
opaque cursor.

## Run it

**CLI (no install required):**

```bash
npx @mockoon/cli start --data mockoon/offline-notes.json --port 8080
```

**Desktop app:** File → Open environment → select `offline-notes.json`, then press ▶.

The debug build already points at `http://10.0.2.2:8080/` (the emulator's alias for the host)
through `BuildConfig.API_BASE_URL`. For a physical device, change that value in
`data/network/build.gradle.kts` to your machine's LAN IP.

## Scripting a scenario

Each route has multiple example responses (Applied / Conflict / Rejected / 401 / 429 / 410),
only one enabled (`"default": true`) at a time. In the desktop app, click a response to make it
active, or add your own [response rules](https://mockoon.com/docs/latest/rules/overview/) keyed
off the request body (e.g. match on `noteId` to conflict one specific note and apply everything
else) — this is where per-note failure injection lives now that the app ships no debug tooling.
