# Adding a language (translating openCook)

openCook is fully localizable by editing plain files. A language is translated in (at most)
**three places**; everything has an English fallback, so a partial translation already works.

There are three independent things to translate:

1. **App UI** — Android string resources (menus, buttons, messages).
2. **App domain word-lists** — grocery-aisle keywords, staples, units, main-protein keywords,
   category/meal aliases and a few grammar helpers (these make the shopping list group correctly,
   keep foreign units, and keep the meal planner varied). Loading these needs **one line of code**
   (registering the language — see §2); nothing else in the app is language-specific.
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
  **verbatim**, never converted. Multi-word units are matched up to three words, so
  `cuillère à soupe` works — no need to invent a one-word abbreviation.
- `cat_alias_*` / `mealtype_alias_*` — the words your language uses for the eight recipe
  categories and the four meal types, so a hand-written or imported recipe that says
  `viande` / `dîner` still lands on the `meat` / `dinner` key. The keys themselves always
  match and need no entry.

### Optional: grammar helpers

Everything above is vocabulary. Four more lists describe how your language *builds* ingredient
names — leave them out and the English/German fallback applies, so matching just gets less precise:

- `ingredient_leading_noise` — measure and vague-amount words that leak into a name field
  ("2 c. à soupe sucre", "une pincée de sel"), stripped so the bare noun is left. Only put words
  here that never *start* a real ingredient name — `feuille` would ruin "feuille de laurier".
- `ingredient_use_phrases` — words opening a trailing *use* phrase: "huile **pour** la friture"
  is still oil.
- `ingredient_plural_suffixes` — how a plural is built (`s`, `x` for French; `en`, `n`, `e`, `s`
  for German).
- `ingredient_head_connectors` — **only for head-initial languages.** German and English put the
  head noun last ("schwarzer Pfeffer", "black pepper") and leave this empty; French, Spanish and
  Italian put it first and mark the modifier (`de`, `d'`, `du`, `des`, `à`). With the connector
  listed, a pantry "huile" covers "huile d'olive". Without it, nothing changes.

And two curated lists, `|`-separated per line — keep both **short**, a wrong entry silently
merges two real ingredients:

- `ingredient_synonyms` — same product, different words (`crème fraîche|creme fraiche`).
- `ingredient_distinctions` — never the same (`lait de coco|lait`). If you filled
  `ingredient_head_connectors`, this is where you stop a staple from swallowing a named variety.

> **Two syntax rules that are easy to miss:**
> - An item that contains a space, or that must keep a leading/trailing space, has to be
>   **wrapped in double quotes**: `<item>"huile d'olive"</item>`, `<item>"ground "</item>`.
>   An unquoted apostrophe is a build error — inside the quotes it is fine.
> - Keywords are matched as a **substring**, so short words need a trailing space to match as a
>   whole word: `<item>"vin "</item>` (otherwise it also matches *vinaigre*),
>   `<item>"eau "</item>`, `<item>"ail "</item>`.

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
`values*/strings.xml` and a branch in `contentLanguageLabel()`
(`ui/settings/HouseholdSettingsScreen.kt`):

```kotlin
"fr" -> stringResource(R.string.lang_french)
```

The `lang_*` values are **endonyms**: every language file spells them the same way
(`Deutsch`, `English`, `Français`) — a list of languages reads best in the languages' own words, so
do *not* translate them into your language.

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
./gradlew lintDebug          # MissingTranslation / ExtraTranslation / MissingQuantity must be clean
./gradlew testDebugUnitTest  # unit tests
./gradlew assembleDebug      # app builds
cd server && pytest -q       # server (the i18n fallback is covered)
```

`MissingQuantity` is the check a new language usually trips: English `<plurals>` only carry
`one`/`other`, while e.g. French also needs `many` and Polish needs `few`. Lint names the missing
form per language.

Lint cannot see the opposite mistake — a `<string>` you copied over and left in English counts as
translated. **Leave such lines out** instead: the English default is used automatically, and the
gap stays visible to lint and to the next translator.

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
