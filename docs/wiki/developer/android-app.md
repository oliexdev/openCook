# Android app

Kotlin, Jetpack Compose + Material 3, single-Activity, MVVM/UDF, Room, Hilt. Package root:
`com.food.opencook`. `minSdk 30`, `targetSdk 36`, Java 17.

## Package layout

Under `app/src/main/java/com/food/opencook/`:

| Package | Contents |
|---|---|
| `ui/` | Compose screens + navigation (`recipes`, `home`, `scan`, `capture`, `review`, `mealplan`, `shoppinglist`, `pantry`, `barcode`, `onboarding`, `settings`, `backup`, `status`, `components`, `theme`, `navigation`) |
| `data/local/` | Room database, entities, DAOs, migrations |
| `data/remote/` | Retrofit APIs + `BaseUrlInterceptor` |
| `data/settings/` | DataStore-backed settings (server URL, household code) |
| `data/discovery/` | mDNS discovery of sync partners — the server and peer phones, told apart by the TXT `role` (`ServerDiscovery`) |
| `data/peer/` | phone-as-responder: embedded Ktor server speaking the sync API (`PeerSyncServer`), switch/Wi-Fi-gated mDNS advertising (`PeerAdvertiser`), standby foreground service for reachability with the app closed (`PeerStandbyService`) |
| `data/image/`, `data/notification/`, `data/recipeimport/` | image store, job notifications, import inbox |
| `sync/` | CRDT sync engine, initiator + responder (see [Sync engine](sync.md)) |
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
  BARCODE_SCAN, BACKUP, PLAN_PICK, SETTINGS_HOUSEHOLD, SETTINGS_SYNC, SETTINGS_APPEARANCE,
  SETTINGS_ABOUT`. Sentinel job IDs `JOB_ID_ALL` (all unreviewed drafts) and
  `JOB_ID_NEW` (manual creation) drive the shared Review screen.
- The Settings tab is a **hub**: each row opens a sub-page and its subtitle shows current state
  (household + size, where sync goes, theme + language, last backup, version). All sub-pages share
  `SettingsRow`/`SettingsSubScreen` from `ui/settings/SettingsComponents.kt` and the single
  `SettingsViewModel`. The server lives under *Sync* rather than in a section of its own — it is one
  possible sync target, not a separate topic.

## Data layer (Room)

`data/local/OpenCookDatabase.kt` — **schema version 1**, persisted as `opencook.db`. Entities:

`RecipeEntity`, `IngredientEntity`, `InstructionEntity`, `NutritionEntity`, `ImageEntity`,
`JobEntity`, `MessageEntity` (the append-only sync log), `ShoppingItemEntity`, `PantryItemEntity`,
`MealPlanEntity`, `MealDayEntity`, `RecipeLikeEntity` (per-member likes, composite key), and
`ProductCacheEntity` (barcode → product cache).

DAOs mirror these: `RecipeDao, JobDao, MessageDao, ShoppingDao, PantryDao, MealPlanDao, MealDayDao,
RecipeLikeDao, ProductCacheDao`. v1 is the released baseline schema — the dev-time migration chain
was squashed into it (`exportSchema=true` commits `app/schemas/.../1.json`), so there are no
migrations yet. Real migrations get registered in `DatabaseModule` as the schema evolves past v1.

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

APIs: `JobsApi`, `SyncApi`, `ImportApi`, `OpenFoodFactsApi`. (There is deliberately no admin API — server maintenance lives in the server's own web console.)

## Dependency injection (Hilt)

`di/` modules, all in `SingletonComponent`: `DatabaseModule` (DB, DAOs),
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
