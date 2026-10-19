# Offline-First Notes

An Android notes app built around one problem, editing offline on several devices and converging
without losing anyone's work. Most of the code is the sync engine, the UI is there to exercise it.

<table>
  <tr>
    <td align="center"><img src="docs/flow-offline-edit.gif" width="200"><br><sub>Edit offline, sync later</sub></td>
    <td align="center"><img src="docs/flow-conflict.gif" width="200"><br><sub>Conflict, resolved by the user</sub></td>
    <td align="center"><img src="docs/flow-rejected.gif" width="200"><br><sub>Server rejects a change</sub></td>
    <td align="center"><img src="docs/flow-remote-change.gif" width="200"><br><sub>Change arrives from another device</sub></td>
  </tr>
</table>

## What's in the repo

- [API contract](https://savvasg28.github.io/offline-notes/), rendered from [`openapi/offline-notes.yaml`](openapi/offline-notes.yaml)
- [High-level architecture](docs/architecture.png), [outbox op lifecycle](docs/op-lifecycle.png) and [sync round](docs/sync-round.png) diagrams
- [Room schema](data/database/schemas/) as exported by Room
- [Mockoon environment](mockoon/offline-notes.json) that plays the server
- [Sync engine tests](domain/src/test/kotlin/com/savvasg/offlinenotes/domain/sync/) and [transaction tests](data/database/src/test/kotlin/com/savvasg/offlinenotes/data/database/)

## Functional requirements

- Create, edit, search and delete notes with the device offline.
- Every edit is saved locally as it is typed. Nothing waits on the network.
- Changes sync to a server in the background and reach the user's other devices.
- Concurrent edits to the same note on two devices are detected and shown to the user, who picks
  a side or merges by hand. Nothing is auto-merged.
- A change the server refuses is shown as such, with the reason. It can be edited again or
  discarded.

## Non-functional requirements

| Requirement | What the client guarantees | What it does not |
|---|---|---|
| Durability | A committed edit survives process death. Dismissing the editor mid-write cannot roll back the last keystroke. | A write still in flight at process death. |
| No silent loss | Remote state never overwrites a note that has an unresolved local change, whether pending, in flight, conflicted or rejected. | Notes with no local change, which a pull is free to update. |
| Idempotent retries | Every operation carries a stable `opId`. A replay is byte-identical to the original. | Server-side dedupe. That is the server's half of the contract. |
| Atomicity | Every multi-table write is one Room transaction. | Nothing. |
| Convergence | With eventual connectivity, a conforming server, and conflicts resolved, replicas converge. | Convergence while a conflict is unresolved. |
| Freshness | Sync is *requested* about 30s after the first edit, immediately on editor close or app foreground, and every 6h as a backstop. | Any upper bound on when it runs. WorkManager's delay is the earliest eligible time, Doze and the network constraint can push it later. |

## Out of scope

Authentication (a 401 is handled as a terminal outcome, but nothing attaches a credential), rich
text, real-time collaboration and CRDTs, attachments, encryption, sharing between users, push
notifications (FCM), iOS/KMP, and the server itself. Each of these has an obvious seam to attach to.

## High-level architecture

![High-level architecture](docs/architecture.png)

- **Presentation.** Compose screens and ViewModels. ViewModels call use cases and observe `Flow`s, never a repository (a Konsist test enforces it).
- **Domain.** Pure Kotlin. Entities, the interfaces the rest of the app implements, use cases, and `SyncEngine`.
- **Data.** One repository behind two interfaces, UI-facing and sync-facing. The sync engine only holds the sync-facing one, so it cannot write note content.
- **Background.** A WorkManager worker runs one sync round. Reset stale in-flight ops, freeze the outbox, push, apply results, pull, apply with the skip rule.
- **`:app`.** Composition root. Hilt graph, `NavHost`, WorkManager config.
- **`:designsystem`.** Theme and the components every screen is built from.

Room is the single source of truth, the UI observes it and never the server.

## API contract

Two endpoints. [Browse the contract](https://savvasg28.github.io/offline-notes/) or
[open it in Swagger Editor](https://editor.swagger.io/?url=https://raw.githubusercontent.com/savvasg28/offline-notes/main/openapi/offline-notes.yaml).
The source is [`openapi/offline-notes.yaml`](openapi/offline-notes.yaml).

| Endpoint | Request | Response |
|---|---|---|
| `POST /push` | Array of frozen ops: `opId`, `type` (CREATE/UPDATE/DELETE), `noteId`, `baseVersion`, `title`, `body` | One result per op, in order: `Applied{newVersion}`, `Conflict{base*, remote*}`, `Rejected{code, message}` |
| `GET /pull?since=` | Opaque cursor, omitted for a full snapshot | `{notes[], nextSyncToken, hasMore}`, paged. Each note is either content or a tombstone |

<details>
<summary><code>POST /push</code> example</summary>

Request, one op per note, `baseVersion` read from the local note at freeze time:

```json
[
  { "opId": "6f1e9c40-…", "type": "CREATE", "noteId": "9b2d1f88-…", "baseVersion": null, "title": "Shopping list", "body": "Milk, bread" },
  { "opId": "8a4c2d10-…", "type": "UPDATE", "noteId": "0d7a3b21-…", "baseVersion": "7", "title": "Weekly review", "body": "Ship the sync engine" }
]
```

Response, one result per op in the same order:

```json
[
  { "type": "Applied",  "opId": "6f1e9c40-…", "newVersion": "1" },
  { "type": "Conflict", "opId": "8a4c2d10-…",
    "baseVersion": "7", "baseTitle": "Weekly review", "baseBody": "Ship it",
    "remoteVersion": "9", "remoteTitle": "Weekly review (laptop)", "remoteBody": "Ship it. Ask design." }
]
```

</details>

<details>
<summary><code>GET /pull?since=42</code> example</summary>

```json
{
  "notes": [
    { "noteId": "9b2d1f88-…", "version": "9",  "deleted": false, "title": "Shopping list", "body": "Milk, bread, tea" },
    { "noteId": "0d7a3b21-…", "version": "10", "deleted": true,  "title": null, "body": null }
  ],
  "nextSyncToken": "43",
  "hasMore": false
}
```

</details>

Design decisions.

- **Compare-and-swap.** An op whose `baseVersion` isn't the note's current server version comes back as `Conflict`, with the remote side and, when history still has it, the base side.
- **Per-op independence.** One conflicting or rejected op never fails the batch.
- **Results vs. failures.** `Conflict` and `Rejected` are 200s. 429 and 5xx are retried with the same `opId`s. 401 ends the round. 410 on `/pull` means the cursor expired, so the client re-bootstraps.
- **Client-generated `noteId`.** A note has an id before it has ever synced.
- **Pagination.** `/pull` pages with `hasMore`. A delta page commits with its own cursor, a snapshot page does not, the cursor lands after the last page.

## Data model

Room, schema v1, `BundledSQLiteDriver` for FTS5.

| Table | Holds |
|---|---|
| `notes` | The current local state of every note |
| `outbox` | Every local change the server has not yet acknowledged |
| `note_conflicts` | The three inputs of a staged conflict: base, local, remote |
| `sync_meta` | The pull cursor (one row) |
| `notes_fts` | FTS5 index over `notes`, kept in sync by triggers |

## State diagrams

**Outbox op lifecycle.** One row in `outbox`, from a user edit to the server's acknowledgement. A row is never mutated after freeze.

![Outbox op lifecycle](docs/op-lifecycle.png)

**Sync round.** What one WorkManager run does. Every step is a transaction or idempotent, so a crash at any point is recovered by the next run.

![Sync round](docs/sync-round.png)

## Implementation notes

- **Skip rule.** A pulled change is applied only if the note has no live outbox row. That one check is what protects local edits.
- **Conflicts are detected on push only.** A pull carries no `baseVersion`. Resolution is an ordinary `UPDATE` with a new `opId` and `baseVersion = remoteVersion`, so there is no `/resolve` endpoint.
- **Rejection.** The op ends, the edit stays. The row is kept as `REJECTED` with the server's reason, which keeps the skip rule armed.
- **Bootstrap is a three-call protocol.** Pages apply as they arrive, the sweep of notes the server no longer has runs once over the union, then the cursor advances. A crash mid-way leaves extra notes, not missing ones.
- **The last keystroke is `NonCancellable`.** Dismissing the editor cancels `viewModelScope`, the in-flight write still commits.
- **Sync triggers.** The delay counts from the first edit, not the last, since a debounce never fires during continuous typing.

## Alternatives considered

- **Server-side auto-merge.** Cut. Merged text applied into a note the user is still typing in either reverts under their cursor or drops the other device's change on the next push.
- **Dirty flag instead of an outbox.** Can't say *what* changed and gives no idempotency key. The outbox makes "no pending op" a provable precondition for a pull.
- **Owning a reference server.** An earlier version shipped a Ktor backend. Cut, I wanted the client tested against a contract it doesn't control.
- **Application-scoped write** for the last keystroke. Google's documented pattern, and it worked. One `NonCancellable` line does the same for the only write that needs it.
- **FCM, KMP/iOS.** Both can be added later without touching the sync engine.

## Running the app

1. Install.

   ```bash
   ./gradlew installDebug
   ```

   It works offline from the first launch and needs nothing else. With no server reachable, edits
   stay `PENDING` and the worker retries with backoff.

2. Optional, start the mock server to see sync.

   ```bash
   npx @mockoon/cli start --data mockoon/offline-notes.json --port 8080
   ```

   The debug build points at `http://10.0.2.2:8080/`, the emulator's alias for the host. Each
   route ships several responses, enable one at a time in the Mockoon UI to drive a scenario.

## Tests and checks

- 100 unit tests across every module, `SyncEngine` against fake ports, `NoteTransactions` against a real Room instance.
- 4 instrumented tests for FTS5 search.
- Android Lint with warnings as errors, detekt with type resolution, Konsist for the layering.

```bash
./gradlew test
./gradlew lint detektAll
./gradlew connectedDebugAndroidTest
```

## Tech stack

Kotlin 2.2 · Compose / Material 3 · Room 2.8 · WorkManager · Hilt · Coroutines/Flow ·
Retrofit/OkHttp + kotlinx-serialization · detekt · Konsist · Mockoon.

## License

[MIT](LICENSE).
