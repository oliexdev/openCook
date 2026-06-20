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
trie; the server diffs it against its own to find the **earliest divergence point** and returns only
the messages from there onward, plus its current trie and the household name/settings. The server
caches its trie per household (keyed by message count).

## Images

Images are **not** in the log. They're content-addressed by SHA-256 and synced out-of-band via
`POST /images` / `GET /images/{name}`; only the image **name** appears as a field value in a message.
The client downloads missing images in the background (a few in parallel) after the owning entity
syncs.

## What triggers a sync (client)

`sync/SyncManager.kt` runs sync best-effort: on app start, shortly after a local change (debounced),
and periodically while the app is foregrounded; `work/SyncWorker.kt` covers background runs. If the
server is unreachable it simply retries on the next trigger — nothing blocks and nothing is lost.

## Guardrail: shared vectors

`server/tests/fixtures/sync-vectors.json` holds shared HLC/merge/Merkle test vectors. Both
`server/tests/test_sync.py` and the app's `SharedVectorsTest.kt` load it, so the two engines are
pinned to identical packing, ordering, merge outcomes and Merkle hashes. **Change the engine on one
side → update the other → both test suites must stay green.**
