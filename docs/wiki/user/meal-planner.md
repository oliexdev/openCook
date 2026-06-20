# Meal planner

The meal planner takes the daily "what should we cook?" question off your plate. It works fully
offline.

## The weekly plan

The **Plan** screen shows your week as a calendar of meals. For any day you can:

- **Pick a recipe** yourself — tap the day and search your whole library.
- **Let openCook suggest** one — the "magic" button rolls a dish that fits the week. Use it for the
  whole week at once, or on a single empty day to fill just that one.
- **Pin** a meal so the planner won't move or replace it.
- **Remove** a day's dish — the day is simply left open. (There's no separate "skip": an empty day
  isn't shopped for, and the planner may fill it again next time you suggest a week.)

## How suggestions are chosen

openCook doesn't pick at random. Each suggestion balances a few simple rules, and you can see
**why** a dish was picked:

- **Variety** — it avoids repeating the same dish (or very similar ones) on consecutive days.
- **Ingredient reuse** — it favours dishes that share ingredients with the rest of the week, so you
  buy and use things efficiently instead of letting them go to waste.
- **Leftover days** — big meals can carry over into a following day instead of cooking something new.
- **Preferences** — recipes your household has **liked** get a boost.
- **Freshness** — dishes you cooked recently are less likely to come up again straight away.

## When you cook something else

Plans meet real life. If you open a recipe and mark it **cooked** while a *different* dish was
planned for today, openCook makes today's plan match what you actually cooked. The displaced dish
isn't lost: if you'd already bought (or already have) its ingredients, the rest of the week **shifts
forward by a day** to make room for it; otherwise it's simply dropped from today. A message tells you
what happened, with an **undo**.

A past day you never marked cooked is assumed not to have happened — if its ingredients were bought,
openCook quietly rolls that dish onto the next free day for you.

## From plan to shopping list

Once your week is planned, the ingredients you need flow into the **shopping list** automatically —
already aware of what's in your [pantry](shopping-and-pantry.md), so you only shop for what's
actually missing. See [Shopping list & pantry](shopping-and-pantry.md).

## Sharing the plan

If you're in a household, the plan is **shared**: when one person plans the week, everyone's phone
shows the same plan after syncing.
