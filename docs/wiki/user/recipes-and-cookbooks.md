# Recipes, cookbooks & importing

Everything about getting recipes into openCook and finding them again. All of this works offline
(except the first barcode lookup and web import, which need the internet/server).

## Browsing & searching

The **Recipes** screen lists your whole library. Search by name and filter by tag to narrow things
down. Recipes are grouped into **cookbooks**, so a scanned book stays together.

Open any recipe for the full view — ingredients, steps and (if present) nutrition — plus quick
actions: mark as **cooked**, **like** it, add it to the **meal plan**, or delete it.

On a **tablet in landscape** the library shows as tiles, and a recipe opens as a cooking-friendly
two-column view — ingredients (with the portions stepper) on the left, the steps on the right — so
you don't scroll between them while cooking. The screen also stays awake while a recipe is open.

## Add a recipe by hand

No photo needed:

1. From **Scan**, choose to create a recipe manually (or use the new-recipe action).
2. The **Review** editor opens empty — type the title, add ingredients (with amounts and units),
   steps, servings, times, category and tags.
3. Save. It lands in your library like any other recipe.

## Import from the web (browser extension)

A small browser add-on grabs recipes from cooking websites (Chefkoch, Lecker, Eat-this, and any
site that embeds a standard recipe) and sends them straight to your openCook server. At your next
sync they appear in your library on every phone.

- It only reads the recipe data the page already publishes, and sends it **only to your own
  server** — nothing is shared with third parties.
- Setup and usage are in the extension's own guide: [`extension/`](../../extension/README.md).
- Needs a connected server (it's the server's import inbox the extension fills).

## Scan barcodes

When adding items to your **shopping list** or **pantry**, you can scan a product barcode instead of
typing:

1. Tap the barcode-scan action on the shopping list or pantry.
2. Point the camera at the barcode.
3. openCook looks the product up (via [Open Food Facts](https://world.openfoodfacts.org)) and fills
   in the name for you.

The first time you scan a given product it needs the internet; after that the product is remembered
on the phone, so the same barcode resolves **instantly and offline** next time.

## Likes & "last cooked"

- **Likes** are per person — each family member can like the recipes they enjoy. The meal planner
  uses this (see [Meal planner](meal-planner.md)).
- Marking a recipe **cooked** records when you last made it, which helps the planner avoid serving
  the same thing too often. If you cook it on a day that had a *different* dish planned, the plan
  adjusts to match — see [Meal planner → When you cook something else](meal-planner.md#when-you-cook-something-else).
