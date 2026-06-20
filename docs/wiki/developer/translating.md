# Adding a language (translating openCook)

openCook is fully localizable by editing plain files. A language is translated in (at most)
**three places**; everything has an English fallback, so a partial translation already works.

There are three independent things to translate:

1. **App UI** — Android string resources (menus, buttons, messages).
2. **App domain word-lists** — grocery-aisle keywords, staples, recognized units (these make the
   shopping list group correctly and keep foreign units).
3. **Server extraction** — the AI prompt + duration words + units + category aliases used when a
   photo is scanned.

The examples below add **French (`fr`)**. Replace `fr` with your language's
[ISO 639-1 code](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes) (`es`, `it`, `ja`, …).

---

## 1. App UI strings

Copy the default (English) file and translate the **values**, never the `name=` keys:

```
app/src/main/res/values/strings.xml      →  app/src/main/res/values-fr/strings.xml
```

- Translate every `<string>` and `<plurals>` value. Keep placeholders (`%1$s`, `%1$d`, `%%`) and the
  `name=` attributes exactly as-is.
- English plurals have only `one`/`other`; other languages may need more `quantity` forms.

**Check completeness** with lint — it lists every string you forgot:

```bash
./gradlew lintDebug      # look for "MissingTranslation"
```

Android automatically shows `values-fr/` when the **device UI language** is French. Anything you
leave out falls back to the English default — nothing breaks.

## 2. App domain word-lists

Same pattern with the arrays file:

```
app/src/main/res/values/arrays.xml       →  app/src/main/res/values-fr/arrays.xml
```

Translate the items in each list:
- `grocery_kw_*` — keywords that sort an ingredient into a supermarket aisle (substring match,
  lower-case). E.g. for `grocery_kw_meat_fish` add `poulet`, `bœuf`, `poisson`, …
- `ingredient_staples` — background basics the meal planner ignores when scoring (salt, oil, …).
- `pantry_defaults` — staples seeded into a new household's pantry (keep display capitalization).
- `ingredient_units` — measuring units in your language (`c. à soupe`, `tasse`, …). Units are shown
  **verbatim**, never converted.

These are loaded for the **content language** (see *How the language is chosen* below), not just the
device UI language.

## 3. Server extraction (`app/i18n/`)

Copy the English catalog and translate the values:

```
server/app/i18n/en.json                  →  server/app/i18n/fr.json
```

| key | what to do |
|---|---|
| `text_prompt` | translate the extraction instructions (**carefully** — it's engineering text; a wrong instruction can hurt extraction quality) |
| `box_prompt` | translate the dish-photo prompt |
| `duration_hours` / `duration_minutes` | the words that mark hours/minutes (`heure`, `min`, …) |
| `units` | units in your language |
| `category_aliases` | map your language's category words to the universal keys, e.g. `{"viande": "meat", "poisson": "fish"}` |

`load_i18n("fr")` reads `fr.json`; unknown languages fall back to `en.json`. Units/durations/aliases
are merged with English, so universal tokens (`g`, `ml`, `min`, the category keys) always work even
if you forget one.

## 4. (Optional) Offer it in the in-app picker

Users on a non-matching device can pick the content language under **Settings → Recipe language**.
To add your language there, edit the options list in `ui/settings/SettingsScreen.kt`
(`ContentLanguageDialog`):

```kotlin
"fr" to stringResource(R.string.lang_french),
```

and add `lang_french` to **every** `values*/strings.xml` (the endonym, e.g. `Français`). This step is
**optional** — on a French device the language is selected automatically (the “Follow system”
default), so you only need the picker entry to let people override it.

---

## How the language is chosen

- **UI language** = the device's system language → `values-<lang>/strings.xml`.
- **Content language** (AI extraction, categories, grocery keywords, staples, units) = a
  **household-wide** setting that defaults to the device language and can be overridden in Settings.
  It drives `values-<lang>/arrays.xml` (via the app) and `server/app/i18n/<lang>.json` (sent with
  each scan).
- **Everything falls back to English**, so you can ship a language in stages: translate the UI
  first, the domain lists and the server catalog later.

## Verify

```bash
./gradlew lintDebug          # MissingTranslation / ExtraTranslation must be clean
./gradlew assembleDebug      # app builds
cd server && pytest -q       # server (the i18n fallback is covered)
```

Then set a device/emulator to your language, scan a recipe in that language, and check that the
shopping list groups its ingredients correctly.

---

## Side note: doing this in Weblate later

The file layout above is already standard, so it can be wired into
[Weblate](https://weblate.org/) without any restructuring — translators then use a web UI instead of
editing files. You'd add **two components** to a Weblate project pointing at this repo:

| Component | Format | File mask | Source |
|---|---|---|---|
| App UI + arrays | Android String Resource | `app/src/main/res/values-*/strings.xml` and `…/arrays.xml` | `values/` |
| Server extraction | JSON file | `server/app/i18n/*.json` | `en.json` |

Gate the server `text_prompt` behind review (it's engineering, not UI copy). Until then, the manual
file-editing process above is all you need.
