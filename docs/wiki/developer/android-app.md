# Android app

Kotlin, Jetpack Compose + Material 3, single-Activity, MVVM/UDF, Room, Hilt. Package root:
`com.food.opencook`. `minSdk 30`, `targetSdk 36`, Java 17.

## Package layout

Under `app/src/main/java/com/food/opencook/`:

| Package | Contents |
|---|---|
| `ui/` | Compose screens + navigation (`recipes`, `home`, `scan`, `capture`, `review`, `mealplan`, `shoppinglist`, `pantry`, `barcode`, `onboarding`, `settings`, `admin`, `status`, `components`, `theme`, `navigation`) |
| `data/local/` | Room database, entities, DAOs, migrations |
| `data/remote/` | Retrofit APIs + `BaseUrlInterceptor` |
| `data/settings/` | DataStore-backed settings (server URL, household code) |
| `data/discovery/` | mDNS server discovery (`ServerDiscovery`) |
| `data/image/`, `data/notification/`, `data/recipeimport/` | image store, job notifications, import inbox |
| `sync/` | CRDT sync engine (see [Sync engine](sync.md)) |
| `work/` | WorkManager workers |
| `repository/` | business logic (`RecipeRepository`, `JobRepository`) |
| `di/` | Hilt modules |
| `util/` | ingredient correction, grocery categories, formatting |

## Navigation

Single Activity (`MainActivity.kt`) → `ui/OpenCookApp.kt` hosts a Compose `NavHost` inside a
`NavigationSuiteScaffold` (bottom bar on phones, rail/drawer on tablets). Full-screen routes
(scan, review, recipe detail, …) hide the nav shell.

- Top-level destinations (`ui/navigation/TopLevelDestination.kt`): `HOME, RECIPES, PLAN, SHOPPING,
  SETTINGS`.
- Routes (`ui/navigation/Routes.kt`): `SCAN, CAMERA, REVIEW, RECIPE_DETAIL, EDIT, PANTRY,
  BARCODE_SCAN, ADMIN, PLAN_PICK`. Sentinel job IDs `JOB_ID_ALL` (all unreviewed drafts) and
  `JOB_ID_NEW` (manual creation) drive the shared Review screen.

## Data layer (Room)

`data/local/OpenCookDatabase.kt` — **schema version 15**, persisted as `opencook.db`. Entities:

`RecipeEntity`, `IngredientEntity`, `InstructionEntity`, `NutritionEntity`, `ImageEntity`,
`JobEntity`, `MessageEntity` (the append-only sync log), `ShoppingItemEntity`, `PantryItemEntity`,
`MealPlanEntity`, `MealDayEntity`, `RecipeLikeEntity` (per-member likes, composite key), and
`ProductCacheEntity` (barcode → product cache).

DAOs mirror these: `RecipeDao, JobDao, MessageDao, ShoppingDao, PantryDao, MealPlanDao, MealDayDao,
RecipeLikeDao, ProductCacheDao`. Migrations `MIGRATION_2_3 … MIGRATION_14_15` are registered in
`DatabaseModule`.

## The scan flow (client side)

WorkManager runs an **upload → poll** chain (`work/WorkScheduler.kt`):

1. `UploadJobWorker` → `RecipeRepository.uploadJob()` → `POST /jobs` (multipart image).
2. `PollJobWorker` → `RecipeRepository.refreshJob()` → `GET /jobs/{id}`, returning `Result.retry()`
   while the job is `pending`/`processing`; on `done` it drains the recipes into Room idempotently
   and notifies the user.

While the relevant screen is open, a foreground coroutine polls and updates the ViewModel; when the
app is backgrounded, WorkManager takes over (expedited with a normal-work fallback). The Retrofit
interface is `data/remote/JobsApi.kt`.

## Networking

`di/NetworkModule.kt` builds one shared `Retrofit` (kotlinx.serialization, `ignoreUnknownKeys`) with
a placeholder base URL. `data/remote/BaseUrlInterceptor.kt` rewrites every request's host/port/scheme
at runtime from `SettingsRepository.serverUrl`, so the server address can change without rebuilding
Retrofit (and requests fail fast if no server is configured). Open Food Facts uses a **separate**
Retrofit pointed at `https://world.openfoodfacts.org/` with a custom `User-Agent`.

APIs: `JobsApi`, `SyncApi`, `ImportApi`, `AdminApi`, `OpenFoodFactsApi`.

## Dependency injection (Hilt)

`di/` modules, all in `SingletonComponent`: `DatabaseModule` (DB, DAOs, migrations),
`NetworkModule` (Json, OkHttp, Retrofit, APIs), `SyncModule` (`Stamper`, `SyncTrigger`),
`SettingsModule` (DataStore), `CorrectionModule` (`ImportCorrector`). Screens use
`@AndroidEntryPoint`; workers use `@HiltWorker` + `@AssistedInject`.

## Tests

JVM unit tests under `app/src/test/`, including `SharedVectorsTest.kt`, which runs the shared sync
fixture (`server/tests/fixtures/sync-vectors.json`) against the Kotlin engine. Instrumented tests
(e.g. `RecipeDaoTest`) under `app/src/androidTest/`.

```bash
./gradlew testDebugUnitTest        # JVM unit tests
./gradlew connectedAndroidTest     # instrumented (device/emulator)
```
