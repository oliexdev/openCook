# Family & sync

openCook lets a whole household share the same recipes, meal plan and shopping list across all their
phones — without any account, cloud service or tracking. Sync works two ways, and they combine
freely: with the **phone-to-phone sync** switch, devices on the same Wi-Fi talk **directly to each
other** (even with the app closed), and an optional **home server** covers everything else.

## How sharing works

- Everyone installs the app and **joins the same household** — either on your home server or
  directly from another family phone (see [Getting started](getting-started.md#joining-a-household)).
- After that, changes made on one phone show up on the others automatically.
- A household is identified by a private **invite code**. Anyone with the code can join — so share
  it only with your family. There is no pairing and nothing to configure: the code *is* the trust.

## When does it sync?

Sync happens quietly in the background:

- when you open the app,
- shortly after you make a change,
- and periodically while the app is open.

Each round first tries your server (if the household has one). If it's off or out of reach and
**phone-to-phone sync** is enabled (Settings; on by default in serverless households), the phone
looks for **other family phones on the same Wi-Fi** and syncs with them directly. With the switch
on, your phone also stays **reachable while the app is closed**: a small silent notification marks
that standby, it only ever runs on Wi-Fi, and joining a Wi-Fi network triggers one catch-up sync —
so a phone that was away picks up the family's changes right when it comes home. With the switch
off, the app behaves exactly as if the feature didn't exist (nothing listens, nothing advertises,
no notification).

It's **best-effort**: you never lose changes — they're stored on your phone and delivered the next
time any counterpart (server or phone) is reachable. It doesn't matter who syncs with whom in which
order; everything converges. Even a phone that never met another one directly gets its changes
relayed through the ones in between.

## What does a server add over phone-to-phone?

Phone-to-phone covers the everyday case — shared lists at home — but the server does strictly more:

| | Phones only | With a server |
|---|---|---|
| Recipes, meal plan, shopping list, pantry sync | ✅ same home Wi-Fi (standby keeps phones reachable with the app closed) | ✅ whenever the server is on |
| Recipe photos | ✅ shared between phones | ✅ plus one place that always has all of them |
| Scanning recipes from photos (AI) | ❌ | ✅ runs on the server |
| Import via the desktop browser extension | ❌ | ✅ |
| One-button backup & restore | ❌ (each phone holds a full copy) | ✅ admin screen |
| Away from home | ❌ Wi-Fi only | ✅ over your VPN |
| Cost | a permanent silent notification, a little battery | a machine that runs the server |

In short: **phones only** handles the everyday sharing completely. The **server** adds AI
scanning, browser import, backups and reachability over VPN. You can start serverless and add the
server whenever — nothing has to be set up again.

## Households without a server

You don't need a server at all to share: found the household **on a phone** ("Household without a
server" in onboarding, optionally PIN-protected), and the other phones join it over Wi-Fi. A server
can be added later from **Settings → Add a server** — the household keeps its invite code, so nobody
has to re-join, and everything already shared is uploaded automatically.

## What if two people edit the same thing?

openCook merges changes **field by field**, automatically. If you rename a recipe on your phone
while someone changes its servings on theirs, both edits survive. If two people change the *same*
field at the same time, the most recent change wins — consistently on every device, so all phones
end up identical.

Deletions sync too, so removing a recipe on one phone removes it everywhere.

## Photos

Recipe photos sync as well, downloaded to each phone in the background after the recipe itself
arrives — so the list appears quickly and pictures fill in shortly after. This works phone-to-phone
too: each phone shares the photos it has.

## Backups (admin)

With a server, your data lives there and the person who set it up (the **admin**) can make and
restore **backups** from the app's admin screen — a single archive containing the database and all
images. Because every phone re-syncs from the server, the phones themselves need no backup. See
[Self-hosting → Backups](../developer/self-hosting.md#backup--restore). In a serverless household
every phone holds the full data — any one of them can restore the family's world, but there is no
one-button backup; consider adding a server if that matters to you.

## Privacy

There is no account and nothing in the cloud. Sync stays on your **home network (or VPN)**, scoped
to your household's invite code. Phone-to-phone sync is opt-in (one switch) and only ever runs on Wi-Fi;
over mobile data your phone is never reachable, and in a foreign Wi-Fi a stranger without your
invite code can see at most that a household with your chosen name exists.
