# openCook documentation

Welcome to the openCook docs. They're split into two tracks — pick the one that fits you.

## :open_book: For users

How to use the app, day to day.

- [Getting started](user/getting-started.md) — install, first launch, joining a household
- [Scanning recipes from photos](user/scanning-recipes.md) — turn a cookbook page into a recipe
- [Recipes, cookbooks & importing](user/recipes-and-cookbooks.md) — manage, search, add by hand, import from the web, scan barcodes
- [Meal planner](user/meal-planner.md) — plan your week with smart suggestions
- [Shopping list & pantry](user/shopping-and-pantry.md) — one shared list, aware of what you have
- [Family & sync](user/family-and-sync.md) — share everything across the family's phones
- [FAQ](user/faq.md) — common questions

## :hammer_and_wrench: For developers

How openCook is built and how to run/extend it.

- [Architecture overview](developer/architecture.md) — the big picture (app + server)
- [Android app](developer/android-app.md) — modules, screens, data layer, DI
- [Server](developer/server.md) — FastAPI app, extraction pipeline, job worker
- [HTTP API reference](developer/api-reference.md) — every endpoint
- [Sync engine](developer/sync.md) — HLC, per-field LWW, Merkle diffing
- [Self-hosting](developer/self-hosting.md) — Docker, config, backups
- [Building from source](developer/building.md) — toolchain & commands
- [Adding a language](developer/translating.md) — translate the UI, domain lists & extraction

---

> These pages live in the repository under `docs/` so they're versioned and reviewable in pull
> requests. They can also be mirrored to the GitHub **Wiki** (the wiki is a separate
> `…​.wiki.git` repository) if a wiki UI is preferred.
