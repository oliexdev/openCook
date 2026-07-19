&nbsp; <img src="fastlane/metadata/android/en-US/images/icon.png" alt="openCook logo" height="60"/> &nbsp;openCook [![CI](https://github.com/oliexdev/openCook/actions/workflows/ci.yml/badge.svg)](https://github.com/oliexdev/openCook/actions/workflows/ci.yml)
=========
Open-source recipe app that turns cookbook photos into recipes, plans your week and shares the shopping list.

<a href="https://f-droid.org/repository/browse/?fdid=com.food.opencook" target="_blank">
  <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"/>
</a>

openCook is an open-source Android app (with an optional server you host yourself). Take a
picture of a cookbook page and it becomes a tidy, editable recipe. Plan meals for the week,
build a shared shopping list everyone can tick off, and keep it all in sync at home — no
sign-up, no tracking.

> [!NOTE]
> For the latest development state, install the latest [openCook dev](https://github.com/oliexdev/openCook/releases/tag/dev-build) build from the [GitHub release page](https://github.com/oliexdev/openCook/releases) or build it yourself from source (see **Building & Running** below).
> Please be aware that the development version, may contain bugs, and will not receive automatic updates.

# Summary :clipboard:

A recipe and meal-planning app that:
* works **fully offline** — your recipes, plans and shopping lists are always there, no server needed,
* needs **no account** — no sign-up, no cloud, no tracking,
* turns a **photo of a recipe into a ready-to-edit recipe** for you,
* helps you **plan the week** and shop **once** for it,
* lets the **whole family share** the same recipes, plan and list,
* is **open source** and free.

# Features :sparkles:

* **Snap a recipe** — photograph a cookbook page and openCook reads it for you: title,
  ingredients, amounts, steps, cooking time and servings. Several recipes on one page are split
  apart automatically, each with its own picture. (Nutrition is only kept when it's actually printed
  — never made up.)
* **Plan your week** — get sensible meal suggestions that avoid repeats, reuse ingredients
  across days, and turn big meals into leftover days — and you can always see *why* a dish was picked.
* **One shared shopping list** — a weekly list the whole household can tick off
  together. It knows what's already in your pantry, and everyday staples (salt, pepper, oil…) stay
  off the list.
* **Find anything** — recipes group into cookbooks and are fully searchable.
* **Scan to add** — add pantry or shopping items by scanning a barcode (with Open
  Food Facts lookup).
* **Import from the web** — browsing a recipe on your phone (Chefkoch, Lecker, …)? Just **Share →
  openCook** and it's saved, no server needed. On the desktop a small browser extension does the
  same. See [`extension/`](extension/README.md).
* **Stay in sync** — every family member's phone stays up to date, with or without a
  server: with **phone-to-phone sync** (one switch) devices on the same Wi-Fi sync
  **directly with each other**, even while the app is closed; a self-hosted server (if
  you run one) covers everything else. Changes merge automatically, even after
  someone's been offline.

# How it works :gear:

openCook comes in two parts:

* **The app** does the whole job on its own — recipes, meal planning and shopping lists all work
  with **no server at all**. A family household can even be founded entirely on a phone: other
  phones join over the home Wi-Fi, and everyone's lists sync **phone-to-phone** (no pairing —
  the household invite code is the shared secret). The phone-to-phone switch keeps each phone
  reachable for the family even with the app closed, at the cost of a small silent notification.
* **An optional server** you host at home adds what phones can't do alone: reading recipes
  from photos (the AI runs only on *your* machine), browser import, backups — and sync that
  also works while nobody has the app open. You can add it to a serverless household later
  in Settings, keeping the same invite code.

If the server is off or out of reach, the app keeps working: photo scans queue until it's back,
and the family's lists keep syncing directly between phones on the same Wi-Fi.

# Building & Running :hammer:

### Android app

Build it with Gradle. You'll need a JDK 17–21 on `JAVA_HOME` (for example the one bundled with
Android Studio).

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run unit tests
```

### Server (optional)

Needed only for reading recipes from photos and family sync. Requires Python 3.12+ and
[Ollama](https://ollama.com) running on the same machine with the vision model pulled
(`ollama pull qwen2.5vl:7b`).

```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app --reload    # http://localhost:8000/docs
```

Or run it with Docker (the app finds the server automatically on your home network):

```bash
cd server
docker compose up -d --build
```

> [!IMPORTANT]
> The server is meant for your home network only and is mostly unauthenticated by design.
> **Don't expose it to the internet.**

### Browser extension

A small add-on that sends a recipe from a web page to your server. Load it from
[`extension/`](extension/README.md).

# Documentation :books:

Full guides live in [`docs/wiki/`](docs/wiki/README.md) — a **user** track (how to use the app) and a
**developer** track (architecture, server, sync engine, API reference, self-hosting).

# Privacy :lock:

openCook has no ads, no tracking, and asks for no unnecessary permissions. There's no account and
nothing in the cloud. Any AI runs on **your own** server, the family sync stays on your home
network, and family members join with a simple invite code. Without a server, the app never goes
online at all.

# Donations :heart:

If you would like to support this project's further development, the creator of this project or the continuous maintenance of this project, feel free to donate via [![PayPal Donation](https://img.shields.io/badge/PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white)](https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=H5KSTQA6TKTE4&source=url) or become a [![GitHub Sponsor](https://img.shields.io/badge/sponsor-30363D?style=for-the-badge&logo=GitHub-Sponsors&logoColor=#white)](https://github.com/sponsors/oliexdev). Your donation is highly appreciated. Thank you!

# Contributing :+1:

Found a bug, have an idea, or a question? Please open an issue or comment on an existing one. To
contribute code, fork the repository and send a pull request.

# Screenshots :eyes:

<table>
  <tr>
    <th><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/1_recipes.png' width='200px' alt='Recipes' /></th>
    <th><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/2_recipe_detail.png' width='200px' alt='Recipe detail' /></th>
    <th><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/3_meal_planner.png' width='200px' alt='Meal planner' /></th>
    <th><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/4_shopping_list.png' width='200px' alt='Shopping list' /></th>
  </tr>
</table>

# License :page_facing_up:

openCook is licensed under the GPL v3 (see the `LICENSE` file for the full notice).

    Copyright (C) 2026  olie.xdev <olie.xdeveloper@googlemail.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>
