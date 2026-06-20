# Server extraction i18n

Per-language resources for the AI recipe extraction (`app/extraction.py` → `load_i18n`).
One JSON file per content language; `en.json` is the source/fallback.

Each file:

| key | meaning |
|---|---|
| `text_prompt` | the main extraction prompt (qwen2.5vl) — **engineering, review changes carefully** |
| `box_prompt` | the dish-photo bounding-box prompt |
| `duration_hours` / `duration_minutes` | words that mark hours/minutes in prep/cook times |
| `units` | recognized measuring units (kept verbatim; never converted) |
| `category_aliases` | maps language words to the universal category keys (`pasta/meat/…`) |

Loading: `load_i18n("xx")` reads `xx.json`, falling back to `en.json` for unknown languages.
`units`, `duration_*` and `category_aliases` are **unioned with English**, so universal tokens
(g, ml, "min", the category keys) always match. The `text_prompt`/`box_prompt` use the language's
own text, else English.

## Adding a language (e.g. Japanese)

Copy `en.json` to `ja.json` and translate the values. No code change needed — the app sends the
household content language with each scan and the loader picks the matching file.

## Weblate

Add a **second Weblate component** for the server, format **JSON file**, file mask
`server/app/i18n/*.json`, source language file `server/app/i18n/en.json`. Translators then add new
languages here just like the Android strings. Note: `text_prompt` is engineering text — gate it
behind review.
