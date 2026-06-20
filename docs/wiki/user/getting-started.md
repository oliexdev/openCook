# Getting started

openCook is an Android app. On its own it already does a lot — managing recipes, planning meals
and keeping a shopping list all work **with no server and no internet**. An optional server you
host at home adds two extra powers: reading recipes from photos, and keeping the whole family's
phones in sync.

## Install the app

There's no Play Store / F-Droid release yet, so you build the app from source — see
[Building from source](../developer/building.md). The result is a normal `.apk` you install on your
phone (Android 11 / API 30 or newer).

## First launch

On first start openCook asks how you want to use it:

- **Just on this phone** — start using it right away. Your recipes, plans and shopping list live
  only on this device.
- **Connect to a home server** — if someone in your household runs the openCook server, join it to
  unlock photo scanning and family sync.

You can always connect to a server later from **Settings**.

## Joining a household

A *household* is the shared space your family's phones sync into. To join one you need a running
server (see [Self-hosting](../developer/self-hosting.md)) on the same Wi-Fi/LAN.

1. Open onboarding (first launch, or **Settings → connect to server**).
2. The app **auto-discovers** servers on your network and lists them. Pick yours (it shows up as
   "openCook" or whatever name the server was given).
   - No server appears? Type its address by hand, e.g. `http://192.168.1.50:8000` (this is needed
     over VPN, or on an emulator).
3. Choose a household:
   - **Join** an existing one from the list (enter its PIN if it's protected), or
   - **Create** a new household and give it a name.
4. Done. The other phones in your family join the **same** household to share everything.

> The first person to set up the server also sets an **admin password** — keep it; it's needed for
> backups and server maintenance (see [Family & sync](family-and-sync.md)).

## What works offline

Everything except the two server-backed features:

| Works offline | Needs the server |
|---|---|
| Browse / edit / create recipes | Scanning a recipe from a photo |
| Meal planning | Sync between phones |
| Shopping list & pantry | Web import via browser extension |
| Search, cookbooks, barcode lookup* | |

\* Barcode scanning works offline for products you've scanned before; the first lookup of a new
product needs internet (Open Food Facts).

Sharing a recipe page to openCook from your phone's browser needs internet but **not** the server —
the app reads and saves the recipe on its own.

When the server is off, photo scans are simply **queued** and processed automatically the next time
it's reachable — you never have to wait.
