# openCook — Recipe Import (Browser Extension)

A small Manifest V3 web extension that detects the embedded `schema.org/Recipe`
(JSON-LD) on a recipe page and sends it — **including the image** — to your
self-hosted openCook server. The app picks the import up automatically on its next
sync and distributes it to every device in the household.

Works in Chromium-based browsers only (Vivaldi, Chrome, Brave, Edge) — not Firefox.

## Install (unpacked)

1. Open `vivaldi://extensions` (or `chrome://extensions`).
2. Turn on **Developer mode** in the top right.
3. **"Load unpacked"** → select this `extension/` folder.
4. Click the openCook icon → **"Open settings"** (or right-click → Options):
   - **Server address**: e.g. `http://192.168.1.50:8000` (include the port, no trailing `/`).
   - **Household code**: the same invite code (`X-Household-Code`) the app uses when
     joining a household.
   - **"Test connection"** checks the server (`GET /health`).

## Usage

On a recipe page (Chefkoch, Lecker, Eat-this, …), click the openCook icon → the title
and an image preview appear → **"Import"**. The recipe lands in the server inbox; on the
app's next sync it shows up in the library (a snackbar reports it). If the server happens
to be off, the import is buffered locally and re-sent automatically the next time you open
the popup.

If no recipe is found on a page, the popup says so clearly — that means the page embeds no
machine-readable recipe.

## Notes

- **Private & self-hosted.** The extension is a personal tool (like a bookmark): it only
  reads the recipe that the page already exposes in machine-readable form and sends it
  exclusively to your own server. No data is redistributed and no third parties are
  contacted.
- **Permissions**: `storage` (server address + code), `activeTab`/`scripting` (read the
  recipe from the current page), `host_permissions` (load the page's image and send it to
  the server).
- No external dependencies — plain vanilla JS, GPLv3 like the rest of openCook.
