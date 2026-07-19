# Sync engine

openCook syncs without a third-party sync product: the same CRDT engine is implemented twice — in
Kotlin (`app/src/main/java/com/food/opencook/sync/`) and in Python (`server/app/sync.py`) — and both
must satisfy a shared test fixture so they converge **byte-identically**.

## Model: append-only message log

Every local change is recorded as one immutable **message**:

```
(timestamp, dataset, row_id, col_key, value)
```

- `dataset` = logical table (`recipes`, `shopping`, `pantry`, …), `row_id` = entity UUID,
  `col_key` = field name, `value` = the field's value as a JSON string.
- The log is append-only; the current state of any entity is the **projection** of its messages.
- Deletes are tombstones (a field/row marked deleted) so removals propagate.

On the server these rows are the `messages` table (`SyncMessage`), with `timestamp` as the primary
key — which makes pushing **idempotent**.

## Clock: Hybrid Logical Clock (HLC)

Each message is stamped by an HLC `(millis, counter, node)`:

- `pack()` → a fixed-width, lexicographically-sortable string: `"{millis:015d}-{counter:04X}-{node}"`.
  Lexical order == logical order.
- `send(now)` bumps the counter within the same millisecond, resets it on a new millisecond;
  `recv(remote, now)` advances past the max of local/remote/now. Counter overflow raises a clock-drift
  error.
- The pack format is **identical** in both languages — that's what makes cross-device convergence
  exact.

## Merge: per-field last-write-wins

Applying a message is a pure, commutative operation: a message is written only if its `timestamp` is
greater than the stored timestamp for that exact `(dataset, row_id, col_key)`. So:

- different fields of the same row merge cleanly (concurrent edits to *different* fields both win);
- a genuine conflict on the *same* field resolves to the higher HLC timestamp — deterministically,
  so every device reaches the same result regardless of message arrival order.

## Diffing: Merkle trie

To avoid resending the whole log every sync, both sides build an incremental **Merkle trie** over
message timestamps (minute-floor buckets, FNV-1a 32-bit hashes). `POST /sync` carries the client's
trie; the responder diffs it against its own to find the **earliest divergence point** and returns
only the messages from there onward, plus its current trie and the household name/settings. The
Python server caches its trie per household (keyed by message count); the phone responder rebuilds
it per exchange (phone logs are small).

## Images

Images are **not** in the log. They're content-addressed by SHA-256 and synced out-of-band via
`POST /images` / `GET /images/{name}`; only the image **name** appears as a field value in a message.
The client downloads missing images in the background (a few in parallel) after the owning entity
syncs.

## Peer-to-peer: a phone as the responder

The responder role is not server-only. With the household's **phone-to-phone switch** enabled,
each phone runs an embedded Ktor (CIO) server implementing the same wire contract — `POST /sync`,
the image blob endpoints and the household list/join flow — and advertises `_opencook._tcp` with
TXT `role=peer` via NSD (the Python server advertises `role=server`). Key pieces:

- `data/peer/PeerSyncServer.kt` — the routes. Every endpoint except household list/join requires
  the `X-Household-Code` (stricter than the Python server: image GETs too); an unknown code
  answers 404 like the server's `resolve_household`.
- `data/peer/PeerAdvertiser.kt` — lifecycle: the responder runs while the household's
  **phone-to-phone switch** (`SettingsRepository.p2pEnabled`; default ON for serverless
  households, OFF with a server) is enabled AND either the app is foregrounded
  (`ProcessLifecycleOwner`) or the standby service holds it, AND an active Wi-Fi network exists.
  Everything is torn down when the last condition drops. Switch off ⇒ the app behaves exactly
  pre-P2P (no listener, no advertisement, no peer fallback).
- `data/peer/PeerStandbyService.kt` — the "reachable with the app closed" half of the switch: a
  minimal `specialUse` foreground service (silent IMPORTANCE_MIN notification; `dataSync` would
  be runtime-capped at 6 h on Android 15+) that holds the advertiser's standby signal and fires
  one catch-up sync when a Wi-Fi network appears ("coming home"). Started/stopped by the
  advertiser's controller from the switch + household state; background-start denials are
  retried on the next app open.
- `sync/SyncResponder.kt` — the responder logic (ingest pushed messages, Merkle diff, answer with
  the missing tail, piggyback household meta). It shares the apply/projection path with the
  initiator via `sync/MessageApplier.kt`, which deliberately never fires a sync trigger — that's
  what keeps two foregrounded peers from ping-ponging.
- `SyncEngine` tries targets in order: the configured server first, then peers discovered on the
  LAN (via a second, `@Named("peer")` OkHttp/Retrofit stack so peer calls can't reroute unrelated
  in-flight requests). The idempotent log makes mixed server+peer rounds converge, including
  transitively (A↔B, later B↔C ⇒ C has A's changes).

Households can be founded **serverless**: the phone mints the uuid + invite code locally
(onboarding), other phones join through the peer's household endpoints, and a server can be
attached later — `POST /households` accepts a client-supplied `id`/`invite_code` idempotently, so
existing members stay valid. Household meta (name/settings/PIN) has no authoritative store between
peers, so it travels with an HLC stamp (`household_hlc`); the newest copy wins everywhere.

## What triggers a sync (client)

`sync/SyncManager.kt` runs sync best-effort: on app start, shortly after a local change (debounced),
and periodically while the app is foregrounded; `work/SyncWorker.kt` covers background runs. If the
server is unreachable the round falls back to reachable peers, and otherwise simply retries on the
next trigger — nothing blocks and nothing is lost.

## Guardrail: shared vectors

`server/tests/fixtures/sync-vectors.json` holds shared HLC/merge/Merkle test vectors. Both
`server/tests/test_sync.py` and the app's `SharedVectorsTest.kt` load it, so the two engines are
pinned to identical packing, ordering, merge outcomes and Merkle hashes. **Change the engine on one
side → update the other → both test suites must stay green.**
