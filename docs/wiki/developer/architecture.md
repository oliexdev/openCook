# Architecture overview

openCook is a monorepo with two halves that talk over HTTP:

```
┌──────────────────────────┐         ┌───────────────────────────────┐
│  app/  (Android client)  │  HTTP   │  server/  (FastAPI backend)   │
│  Kotlin · Compose · Room │ ──────▶ │  SQLite · in-process worker   │
│  offline-first, MVVM/UDF │ ◀────── │  Ollama (vision) · mDNS       │
└──────────────────────────┘         └───────────────────────────────┘
```

- **`app/`** — the Android client. It is the whole product on its own: recipe management, meal
  planning and shopping lists run entirely on-device with no server — and with the phone-to-phone
  switch enabled, each phone itself answers the sync contract for other phones on the home Wi-Fi
  (embedded Ktor server + mDNS `role=peer`; a standby foreground service keeps it reachable with
  the app closed), so a household syncs **peer-to-peer** without any backend.
- **`server/`** — an **optional** Python backend that adds what phones can't do alone: AI recipe
  extraction from photos, browser import, server-side archives, and reachability beyond the home Wi-Fi (VPN).

## Design principles

- **Offline-first.** The app never depends on the server for core use. Anything server-backed
  (scan, sync, web import) degrades gracefully and retries.
- **No account, LAN/VPN trust model.** There is no per-user authentication. A *household* is the
  sync scope, joined by a shared **invite code** sent in the `X-Household-Code` header. The server
  is meant for a trusted home network and must not be exposed to the internet.
- **schema.org/Recipe as the interchange format.** Extraction produces
  [schema.org/Recipe](https://schema.org/Recipe) JSON-LD (with `openCook*` extension fields for
  structured ingredients, servings, etc.). The app's internal Room model is a richer superset.
- **GPLv3-compatible, permanently-free dependencies only.** This is why sync is self-implemented
  rather than using a source-available product.

## How the two halves cooperate

**Recipe scan (async):**
photo → app `POST /jobs` → server stores the image and queues a `Job` → an in-process worker calls
**Ollama** (`qwen2.5vl:7b`) → schema.org recipe(s) + cropped dish photos → app polls `GET /jobs/{id}`
(active coroutine while open, WorkManager when backgrounded) → **Review** screen → saved to Room.

**Sync (delta):**
both the app (Kotlin) and the server (Python) implement the **same** CRDT sync engine — an
append-only message log keyed by **Hybrid Logical Clock** timestamps, **per-field last-write-wins**
merge, and **Merkle-trie** diffing so only changed messages cross the wire. Images sync out-of-band
(content-addressed by SHA-256); only their IDs travel in the log. A shared test fixture
(`server/tests/fixtures/sync-vectors.json`) pins both implementations to byte-identical behaviour.
Because the responder role is also implemented in the app, a sync round works against the server
*or* another phone with the identical wire contract; the idempotent log makes any mix converge —
see [Sync engine → Peer-to-peer](sync.md#peer-to-peer-a-phone-as-the-responder).

## Where to read next

- [Android app](android-app.md) — client internals
- [Server](server.md) — backend internals & extraction
- [HTTP API reference](api-reference.md) — every endpoint
- [Sync engine](sync.md) — the CRDT in detail
- [Self-hosting](self-hosting.md) and [Building from source](building.md)
