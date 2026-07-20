# Backup & restore

Your recipes live on your phone. If the phone is lost, so are they — unless you have a
backup. openCook writes one as a single ZIP file that you control: no account, no cloud,
nothing uploaded anywhere.

This works in **every** household, with or without a server. It matters most if you have
no server and only one phone, because then the phone is the only copy of everything.

## Making a backup

**Settings → Backup & restore → Create backup.** Pick where the file should go — your
Downloads folder, an SD card, a cloud drive, whatever your phone offers — and openCook
writes it there.

The file is named like `opencook-backup-20260719-140311.zip` so backups sort by date.

## Automatic backups

Doing it by hand works exactly until you forget. Turn on **Back up automatically**, pick a
folder once, and openCook writes a fresh backup **every night while the phone is
charging**, keeping the five newest and deleting older ones.

It skips writing when nothing has changed since the last run, so an untouched recipe
collection doesn't rewrite the same large file night after night.

If a backup ever fails, the reason appears right under the setting — there is no
notification, so glance here if you want to be sure it's still working.

## Restoring

**Settings → Backup & restore → Restore backup**, then pick a backup file. openCook shows
you what's inside — the date, how many recipes and photos, which household it came from —
before anything happens.

Two things worth knowing:

- **Nothing is deleted.** Restoring only adds things back. Recipes you created after the
  backup was made stay exactly where they are.
- **Deleted recipes come back.** If you deleted something by mistake, restoring an older
  backup brings it back. That is the point.

Restoring twice changes nothing the second time, so if you're unsure whether a restore
finished, just run it again.

In a shared household, a restore syncs out to the other phones like any other change.

## What's in the file

| Included | Not included |
|---|---|
| Recipes, with amounts, steps and notes | Your household code and PIN |
| Recipe photos | Which recipes you personally liked |
| Shopping list | Barcode lookups (they refill themselves) |
| Pantry | |
| Meal plan | |

The recipes are stored as **schema.org/Recipe** — the format recipe websites and other
recipe apps use — so you can open `recipes.json` in any text editor and read it, or take
your recipes elsewhere. The whole ZIP can also be fed to openCook's normal
**Add recipe → Import from file**, which will pull in just the recipes.

## The file holds no password

A backup contains **no household code and no PIN**. Nobody can join your household with
it. That means you can store it in a cloud drive or email it to yourself without
worrying, and you can restore it into **any** household — including a friend's, if you
want to pass recipes along.

The flip side: after reinstalling the app, first join or create your household as usual,
*then* restore the backup.

## If you run a server

Your server keeps its own, separate backups of the complete database — including the full
edit history, which lets it be rolled back to an exact earlier state. Those are managed in
the server's web console at `http://<your-server>:8000/admin/`, not in the app. See
[Self-hosting → Backup & restore](../developer/self-hosting.md#backup--restore).

Having both is not redundant: the phone backup is portable and readable, the server backup
is complete and unattended.
