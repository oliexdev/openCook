# Server

Python 3.12+ FastAPI app with a SQLite database, an **in-process** async job worker, an Ollama
client for recipe extraction, and mDNS advertising. No external queue or services.

## Layout

```
server/app/
  main.py          # FastAPI app, lifespan (init_db, WAL checkpoint, mDNS, worker), router wiring
  config.py        # Settings — OPENCOOK_* env vars
  db.py            # SQLAlchemy engine, SQLite WAL, init_db(), get_session()
  models.py        # ORM: Household, ServerConfig, SyncMessage, ImportRecord, Job
  schemas.py       # Pydantic request/response DTOs
  extraction.py    # qwen2.5vl pipeline → schema.org/Recipe + cropped photos
  ollama_client.py # async HTTP client to Ollama (retries/backoff)
  worker.py        # background job loop
  sync.py          # HLC + per-field LWW + Merkle trie  (see sync.md)
  discovery.py     # mDNS advertiser (_opencook._tcp)
  security.py      # salted SHA-256 hashing (PINs, admin password)
  backup.py        # DB+images snapshot/restore
  api/             # routers: jobs, sync, imports, households, images, admin (+ _deps.py)
  static/          # admin.html — self-contained web admin console (served at /admin/)
server/scripts/    # backup.py, restore.py, extraction experiments
server/tests/      # pytest; fixtures/ (incl. sync-vectors.json)
```

`main.py` creates the app (title "openCook server"), adds GZip + permissive CORS (so the browser
extension and LAN apps work), and on startup runs `init_db()`, a WAL checkpoint, the
`MdnsAdvertiser`, and the async `worker_loop()`. `GET /health` returns `{"status": "ok"}`. Full
endpoint list: [HTTP API reference](api-reference.md).

## Database

`db.py` opens SQLite at `{OPENCOOK_DATA_DIR}/opencook.db` in **WAL** mode
(`check_same_thread=False` so the worker and request threads share the engine; `foreign_keys=ON`,
`synchronous=NORMAL`). `init_db()` runs `Base.metadata.create_all`.

ORM tables (`models.py`):

| Table | Purpose |
|---|---|
| `households` | sync scope: `invite_code` (unique, immutable), `name`, `settings_json`, optional salted PIN |
| `server_config` | singleton row holding the salted **admin password** |
| `messages` (`SyncMessage`) | append-only sync log; PK = HLC-packed `timestamp`; `(household_id, dataset, row_id, col_key, value)` |
| `imports` | browser-extension inbox; `status` pending/claimed/consumed; `recipe_json`, optional `image_name`, `source_url` |
| `jobs` | extraction jobs; `status` pending/processing/done/error; `image_path`, `stage`, `result_json`, `error` |

## Extraction pipeline

`extraction.py` turns a photo into recipes in a few steps:

1. **Orient & resize** — `ImageOps.exif_transpose`, then `_smart_resize` to longest side ≤ 1008 px
   with both dimensions a multiple of 28 (qwen2.5vl's pixel budget; keeps box coords mapping 1:1).
2. **Text call** — the content-language prompt (`load_i18n(language).text_prompt`) → Ollama →
   `{"recipes": [{title, servings, category, tags, prep_time, cook_time, ingredients[], steps[],
   nutrition, notes}]}` (lenient JSON parse). `category` is a universal key (`pasta/meat/…`).
3. **Box call** — `box_prompt` → `{"dish_photos": [{recipe_title, box:[x1,y1,x2,y2]}]}` in
   sent-image pixels (best-effort; failures are non-fatal).
4. **Assign** — `_assign_boxes` greedily matches each photo to a recipe by title similarity (1:1).
5. **Crop** — scale box coords back to the original image, crop, save to `images_dir` (JPEG q88).
6. **Convert** — `to_schema_org` emits schema.org/Recipe JSON-LD plus `openCook*` extension fields
   (`openCookIngredients`, `openCookServings`, `openCookCategory`, `openCookTags`, `openCookNotes`,
   `openCookSourcePhoto`), `nutrition` (only if printed), and ISO-8601 `prepTime`/`cookTime`.

Ollama is reached via `ollama_client.py` at `OPENCOOK_OLLAMA_BASE_URL`, model
`OPENCOOK_OLLAMA_MODEL` (default `qwen2.5vl:7b`), with retries + backoff on transport/5xx errors.

**Localization:** prompts, duration words, units and category aliases live in per-language JSON
under `app/i18n/` (`en.json` = source/fallback), loaded by `load_i18n(language)` where `language`
is the household content language sent with the job. Adding a language = a new `<lang>.json`
(translatable via a second Weblate JSON component) — no code change. See `app/i18n/README.md`.

## Job worker

`worker.py` is an in-process async loop started in the app lifespan:

1. Poll the `jobs` table every `OPENCOOK_WORKER_POLL_INTERVAL` seconds (default 2.0).
2. Claim the first `pending` job → set `processing`.
3. Run `RecipeExtractor.extract()` with an `on_stage` callback that updates `Job.stage`
   (e.g. `reading_text`, `detecting_photos`) for the app's progress strip.
4. On success → `done` + `result_json`; on failure → `error` + message.

## Config

All env vars are prefixed `OPENCOOK_` (`config.py`, `.env` supported): `DATA_DIR`, `HOST`, `PORT`,
`SERVER_NAME`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `WORKER_POLL_INTERVAL`, `BACKUP_DIR`,
`BACKUP_KEEP`. See the table in [Self-hosting](self-hosting.md).

## Tests

```bash
cd server && pytest -q
```
`tests/test_sync.py` runs the shared CRDT vectors from `tests/fixtures/sync-vectors.json` (the same
file the Kotlin `SharedVectorsTest` uses), guaranteeing both engines converge identically.
