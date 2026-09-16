# Meal planner

The meal planner takes the daily "what should we cook?" question off your plate. It works fully
offline.

## The rolling plan

The **Plan** screen is one continuous list of days — the past week behind you, the week ahead in
front — grouped under week headings and opened on today. There are no pages to flip: moving a dish
to another week is just scrolling.

The plan **fills itself**. Whenever you open it, openCook re-anchors the window on today, rolls
forward anything from a past day you never cooked, and proposes a dish for the free days ahead. You
never have to ask for a plan; you only correct the one you're given.

For any day you can:

- **Pick a recipe** yourself — tap an empty day and search your whole library.
- **Swap the dish** — the swap button next to it opens the picker, led by openCook's own next-best
  proposal and the reason for it.
- **Swipe left to remove** a dish — the day is simply left open. (There's no separate "skip": an
  empty day isn't shopped for, and the planner may fill it again.)
- **Swipe right to shop** for it — its missing ingredients go on the shopping list, undoably.

A dish you planned or cooked within the last 10 days is never proposed again, so the plan doesn't
circle back on itself.

## How suggestions are chosen

openCook doesn't pick at random. Each suggestion balances a few simple rules, and you can see
**why** a dish was picked:

- **Variety** — it avoids repeating the same dish (or very similar ones) within the plan.
- **Ingredient reuse** — it favours dishes that share ingredients with the rest of the week, so you
  buy and use things efficiently instead of letting them go to waste.
- **Leftover days** — big meals can carry over into a following day instead of cooking something new.
- **Preferences** — recipes your household has **liked** get a boost.
- **Freshness** — dishes you cooked recently are less likely to come up again straight away.

## When you cook something else

Plans meet real life. If you open a recipe and mark it **cooked** while a *different* dish was
planned for today, openCook makes today's plan match what you actually cooked. The displaced dish
isn't lost: if you'd already bought (or already have) its ingredients, the following days **shift
forward by a day** to make room for it; otherwise it's simply dropped from today. A message tells you
what happened, with an **undo**.

A past day you never marked cooked is assumed not to have happened — if its ingredients were bought,
openCook quietly rolls that dish onto the next free day for you.

## Looking back

A **What we've cooked** card in the plan opens the retrospective: everything your household has
actually cooked, newest month first. It's the answer to "what did we have three weeks ago?" — and a
quick way back into a dish that worked.

## From plan to shopping list

Once your week is planned, the ingredients you need flow into the **shopping list** automatically —
already aware of what's in your [pantry](shopping-and-pantry.md), so you only shop for what's
actually missing. See [Shopping list & pantry](shopping-and-pantry.md).

## Sharing the plan

If you're in a household, the plan is **shared**: when one person changes a day, everyone's phone
shows the same plan after syncing.
