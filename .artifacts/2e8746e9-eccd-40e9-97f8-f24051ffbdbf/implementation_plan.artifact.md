# Implementation Plan - Multi-Meal Planning and Month Planning

This plan introduces support for multiple meals per day (Breakfast, Lunch, Dinner) and extends the planning window to support a full month.

## User Review Required

> [!IMPORTANT]
> **Database Migration**: Adding the `slot` field to the `meal_plan` table will require a database version bump. Existing entries will default to the "Dinner" slot.
>
> **Automatic Generation**: The "Suggest week" feature will currently continue to focus on generating **Dinner** plans automatically. Breakfast and Lunch slots will remain available for manual planning in this initial version, as generating all 3 meals for 7-30 days involves significantly more complex variety constraints.

## Open Questions
- Should "Suggest Week" also try to fill Breakfast and Lunch?
- For Month planning, do you want a 30-day continuous scroll or a calendar-style grid? (Initial implementation will be a list-based month view similar to the current week view).

## Proposed Changes

### [Data Layer]

#### [MODIFY] [MealPlanEntity.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/data/local/entity/MealPlanEntity.kt)
- Add `val slot: String = "dinner"` field.
- Define a `MealSlot` enum for `breakfast`, `lunch`, `dinner`.

#### [MODIFY] [OpenCookDatabase.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/data/local/OpenCookDatabase.kt)
- Increment version to `2`.
- Add a simple migration or allow destructive migration (given the "disposable data" comment).

#### [MODIFY] [MealPlanRepository.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/repository/MealPlanRepository.kt)
- Update `addEntry`, `addCookedEntry`, `replaceDay`, `generateAndSaveWeek` to include the `slot`.

#### [MODIFY] [MealPlanMessageEncoder.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/sync/MealPlanMessageEncoder.kt)
- Include `slot` in the encoded sync messages.

#### [MODIFY] [RecipeExport.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/util/RecipeExport.kt)
- Update `MealPlanExportDto` to include `slot`.

---

### [Logic Layer]

#### [MODIFY] [WeekDates.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/util/WeekDates.kt)
- Add `monthOf(reference: LocalDate)` to return all days of the current month.

#### [MODIFY] [MealPlanner.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/ui/mealplan/MealPlanner.kt)
- Update `generateWeek` to accept a target slot (defaulting to Dinner).

---

### [UI Layer]

#### [MODIFY] [Routes.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/ui/navigation/Routes.kt)
- Update `PLAN_PICK` to include a `slot` argument.

#### [MODIFY] [MealPlanViewModel.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/ui/mealplan/MealPlanViewModel.kt)
- Extend `WeekSelection` to `PlanningWindow` (CURRENT_WEEK, NEXT_WEEK, MONTH).
- Update the `week` flow (rename to `plan`) to group entries by day AND slot.
- Update `moveDish` to handle slot changes.

#### [MODIFY] [MealPlanScreen.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/ui/mealplan/MealPlanScreen.kt)
- Add "Month" option to the window selector.
- Update `DayCard` to display sections for Breakfast, Lunch, and Dinner.
- Show an "Add" button for each empty slot.
- Display the slot name next to planned items.

#### [MODIFY] [AddToMealPlanSheet.kt](file:///Volumes/DevProjects/rjmlaird/openCook/app/src/main/java/com/food/opencook/ui/recipes/AddToMealPlanSheet.kt)
- Allow users to pick a slot when assigning a recipe.

## Verification Plan

### Automated Tests
- Build and run the app.
- Verify existing "Dinner" plans are preserved after migration.
- Verify adding a recipe to "Breakfast" shows up correctly.
- Verify month view displays all days of the current month.
- Verify "Export all data" includes the new slot information.

### Manual Verification
- Plan a Breakfast and Dinner for the same day.
- Switch to Month view and scroll through the days.
- Drag a Breakfast dish from Monday to Lunch on Tuesday.
