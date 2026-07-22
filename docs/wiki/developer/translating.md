# Adding a language (translating openCook)

openCook is fully localizable by editing plain files. A language is translated in (at most)
**three places**; everything has an English fallback, so a partial translation already works.

There are three independent things to translate:

1. **App UI** — Android string resources (menus, buttons, messages).
2. **App domain word-lists** — grocery-aisle keywords, staples, units, main-protein keywords (these
   make the shopping list group correctly, keep foreign units, and keep the meal planner varied).
   Loading these needs **one line of code** (registering the language — see §2).
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
- `protein_kw_*` — main-protein keywords for the meal-planner's variety scoring, so it doesn't
  suggest the same protein twice in a week. The group **keys are fixed** (`protein_kw_poultry`,
  `_fish`, `_mince`, `_pork`, `_beef`, `_lamb`, `_plant`); translate only the keywords, e.g.
  `protein_kw_poultry` → `poulet`, `dinde`. These deliberately overlap with `grocery_kw_meat_fish`
  but are **sub-grouped** (which protein, not just "is it meat") — the variety check needs the
  distinction so chicken-then-fish counts as variety, not a repeat.
- `pantry_defaults` — staples seeded into a new household's pantry (keep display capitalization).
- `ingredient_units` — measuring units in your language (`c. à soupe`, `tasse`, …). Units are shown
  **verbatim**, never converted.

> **Required — register the code** in `ContentLanguages.CODES`
> (`app/src/main/java/com/food/opencook/data/settings/SettingsRepository.kt`):
> ```kotlin
> val CODES = listOf("en", "de", "fr")
> ```
> The lists above are loaded as the **union across every registered language**, so a German recipe
> is still classified correctly on an English phone (and vice versa). **Until `fr` is in `CODES`,
> your `values-fr/arrays.xml` is not loaded at all.** Registering here also makes `fr` appear in the
> in-app picker (§4). Anything you leave out falls back to the English array.

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
| `meal_type_aliases` | map your language's meal words to the universal meal-type keys (`breakfast`, `lunch`, `snack`, `dinner`), e.g. `{"petit-déjeuner": "breakfast", "déjeuner": "lunch", "goûter": "snack", "dîner": "dinner"}` — used when the model answers in the recipe's language instead of emitting the keys |

`load_i18n("fr")` reads `fr.json`; unknown languages fall back to `en.json`. Units/durations/aliases
are merged with English, so universal tokens (`g`, `ml`, `min`, the category keys) always work even
if you forget one.

## 4. (Optional) Name it in the in-app picker

Once the code is registered in `ContentLanguages.CODES` (§2), it **already appears** under
**Settings → Recipe language** — the picker derives its options from that list. It just shows the
uppercase code (`FR`) until you give it an endonym: add a `lang_french` string to **every**
`values*/strings.xml` (e.g. `Français`) and a branch in `contentLanguageLabel()`
(`ui/settings/SettingsScreen.kt`):

```kotlin
"fr" -> stringResource(R.string.lang_french)
```

This is **optional** — on a French device the language is auto-selected (the “Follow system”
default); the label only makes the manual override read nicely.

---

## How the language is chosen

- **UI language** = the device's system language → `values-<lang>/strings.xml`.
- **Content language** (AI extraction, categories, grocery keywords, staples, units, protein
  keywords) = a **household-wide** setting that defaults to the device language and can be overridden
  in Settings. It drives `server/app/i18n/<lang>.json` (sent with each scan). The app-side word-lists
  in `values-<lang>/arrays.xml` are loaded as the **union of all languages in `ContentLanguages.CODES`**
  (not just the active one), so classification never depends on which single language is active —
  that's why registering the code there is required.
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
