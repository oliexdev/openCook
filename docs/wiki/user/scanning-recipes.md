# Scanning recipes from photos

openCook's headline feature: photograph a cookbook page and it becomes a tidy, editable recipe.
This needs a connected [home server](../developer/self-hosting.md) (the AI runs there, on your own
machine — never in the cloud).

## How to scan

1. Tap **Scan** (the camera action).
2. **Take a photo** of the recipe page, or **pick one from your gallery**.
3. The photo is sent to your server and processed in the background. You can keep using the app —
   a status strip shows the progress ("reading text", "detecting photos", …).
4. When it's done, the **Review** screen opens with the extracted recipe ready to check.

If the server is currently off, the scan is **queued as pending** and runs automatically as soon as
the server is back — nothing is lost.

## What the AI reads

For each page it extracts:

- **Title**
- **Ingredients** with amounts and units (kept structured, not just plain text)
- **Steps / instructions**
- **Servings**, **prep time** and **cook time**
- **Category** and **tags**
- **Nutrition** — *only if it's actually printed on the page.* openCook never invents or estimates
  nutrition values.

It also finds the **dish photos** on the page, crops them automatically, and attaches each one to
the matching recipe.

> **Multiple recipes on one page?** No problem — the AI splits them into separate recipes and gives
> each its own picture.

## Review before saving

The Review screen is where you get the final say:

- Fix any misread amount, unit or word.
- Add or remove ingredients and steps.
- Adjust servings, times, category and tags.
- Confirm the attached photo.

Nothing is added to your library until you save it here. The same Review screen is also used when
you [create a recipe by hand](recipes-and-cookbooks.md#add-a-recipe-by-hand).

## Tips for good results

- Photograph **one well-lit page** straight-on; avoid heavy glare and deep shadows.
- A slightly rotated photo is fine — openCook corrects orientation automatically.
- Printed nutrition tables are read when present; handwriting and very stylised fonts are harder.
