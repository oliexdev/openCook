# FAQ

### Do I need the server?
No — for everyday use. Recipes, meal planning and the shopping list all work fully offline on a
single phone. You only need a server for **scanning recipes from photos** and **syncing between
phones**.

### Do I need an account?
No. There's no registration, no cloud and no tracking. Families share data through a self-hosted
server joined by an invite code.

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
when the server comes back; nothing is lost.

### How do family members join?
They install the app, connect to the same server, and join the **same household** using its invite
code. See [Getting started](getting-started.md#joining-a-household).

### Which Android version do I need?
Android 11 (API 30) or newer.

### What languages does it support?
The app and the recipe AI are tuned for German cookbooks, but the recipe format itself is
language-neutral.
