# Family & sync

openCook lets a whole household share the same recipes, meal plan and shopping list across all their
phones — without any account, cloud service or tracking. Sync runs through **your own** home server.

## How sharing works

- Everyone installs the app and **joins the same household** on your home server
  (see [Getting started](getting-started.md#joining-a-household)).
- After that, changes made on one phone show up on the others automatically.
- A household is identified by a private **invite code**. Anyone with the code (and the server's
  address) can join — so share it only with your family.

## When does it sync?

Sync happens quietly in the background:

- when you open the app,
- shortly after you make a change,
- and periodically while the app is open.

It's **best-effort**: if the server is off or out of reach (a home desktop that isn't always on,
for example), the app keeps working and simply tries again later. You never lose changes — they're
stored on your phone and sent when the server is next reachable.

## What if two people edit the same thing?

openCook merges changes **field by field**, automatically. If you rename a recipe on your phone
while someone changes its servings on theirs, both edits survive. If two people change the *same*
field at the same time, the most recent change wins — consistently on every device, so all phones
end up identical.

Deletions sync too, so removing a recipe on one phone removes it everywhere.

## Photos

Recipe photos sync as well, downloaded to each phone in the background after the recipe itself
arrives — so the list appears quickly and pictures fill in shortly after.

## Backups (admin)

Your data lives on the server. The person who set it up (the **admin**) can make and restore
**backups** from the app's admin screen — a single archive containing the database and all images.
Because every phone re-syncs from the server, the phones themselves need no backup. See
[Self-hosting → Backups](../developer/self-hosting.md#backup--restore).

## Privacy

There is no account and nothing in the cloud. The server is meant for your **home network (or VPN)**
only, sync is scoped to your household's invite code, and without a server the app never goes online
at all.
