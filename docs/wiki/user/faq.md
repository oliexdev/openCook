# FAQ

### Do I need the server?
No. Recipes, meal planning and the shopping list all work fully offline, and with the
**phone-to-phone sync** switch family phones on the same Wi-Fi **sync directly with each
other** — even while the app is closed. A household can be founded entirely without a server;
a server adds **scanning recipes from photos**, browser import and access over VPN.

### How do I back up my recipes without a server?
**Settings → Backup & restore.** It writes a ZIP with your recipes, photos, shopping list, pantry
and meal plan to wherever you like, and can do it automatically every night while charging. The
file holds no household code or PIN, so it's safe to keep in a cloud drive — see
[Backup & restore](backup-and-restore.md).

### Do I need an account?
No. There's no registration, no cloud and no tracking. Families share data through a household
joined by an invite code — hosted on your own server, or on nothing but your phones.

### How does phone-to-phone sync work?
It's one switch in Settings (on by default in serverless households, off when you have a server).
Enabled, your phone quietly answers other family phones on the home Wi-Fi — even while the app is
closed (a small silent notification marks that standby, as Android requires). No pairing, no
setup — the household invite code both phones already hold is the shared secret. Over mobile data
a phone is never reachable, and coming home to your Wi-Fi triggers one automatic catch-up sync.

### Is my data sent anywhere?
Without a server, the app never goes online. With a server, your data goes **only to your own
server** on your home network/VPN. The AI that reads recipe photos runs on that server too — never
on someone else's cloud. (The one exception is barcode lookups, which query the public Open Food
Facts database the first time you scan a new product.)

### Does it cost anything?
No. openCook is free and open source (GPL v3).

### Why isn't it on the Play Store / F-Droid?
It's still in active development. For now you build it from source — see
[Building from source](../developer/building.md).

### The AI got something wrong. Can I fix it?
Yes — every scanned recipe opens in a **Review** screen before it's saved, where you can correct
anything. openCook also never invents nutrition values; it only keeps what's actually printed.

### Can multiple recipes on one page be scanned at once?
Yes. The AI splits a page into separate recipes and attaches the right photo to each.

### My server is a desktop that isn't always on. Is that OK?
Yes. Sync and photo scans are best-effort and retry automatically. Scans queue as *pending* and run
when the server comes back; nothing is lost — and while it's off, phones with the app open keep
syncing directly with each other.

### How do family members join?
They install the app and join the **same household** — picked from the auto-discovered list (the
server, or a family phone that has the app open). See
[Getting started](getting-started.md#joining-a-household).

### Which Android version do I need?
Android 11 (API 30) or newer.

### What languages does it support?
The app and the recipe AI are tuned for German cookbooks, but the recipe format itself is
language-neutral.
