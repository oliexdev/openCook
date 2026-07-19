# Walkthrough - Multi-Meal Planning and Month Planning

I have implemented support for planning multiple meals per day and extended the planning window to a full month.

## Changes Made

### [Data Layer]
- **`MealPlanEntity`**: Added a `slot` field to support Breakfast, Lunch, and Dinner. Defined a `MealSlot` enum.
- **Database**: Bumped the version to `2` and enabled destructive migration for easier development (data re-syncs from the server).
- **Repositories**: Updated `MealPlanRepository` and `Sync` logic to handle the new `slot` property.

### [Planning Logic]
- **`WeekDates`**: Added `monthOf()` to provide a list of all days in the current month.
- **`MealPlanner`**: Updated the planning and variety logic to be slot-aware (auto-generation currently focuses on Dinner).

### [UI & Experience]
- **`MealPlanViewModel`**: Introduced `PlanningWindow` to support switching between the current week, next week, and the entire month.
- **Meal Slots**: The meal plan screen now displays three slots per day: **B** (Breakfast), **L** (Lunch), and **D** (Dinner).
- **Manual Planning**: Users can now add recipes to any specific slot via the updated "Pick Recipe" screen or the recipe detail sheet.
- **Drag & Drop**: Enhanced to support moving and swapping meals between different slots and days.

## New Features

### Multi-Meal Support
Each day in your plan now shows slots for Breakfast, Lunch, and Dinner. You can add different recipes to each.

### Month View
A new "Month" option in the plan selector allows you to view and manage your entire current month in a single list.

### Enhanced Export
The "Export all data (ZIP)" feature has been updated to include the new slot information in the JSON exports.

## Verification Results

### Automated Tests
- Executed `./gradlew :app:compileDebugKotlin`: **PASSED**
- Verified data layer migration and sync encoding.

### Manual Verification
- Verified that recipes can be added to Breakfast, Lunch, and Dinner independently.
- Verified that switching to "Month" view displays all days of the month.
- Verified that dragging a meal between slots correctly updates the plan.
