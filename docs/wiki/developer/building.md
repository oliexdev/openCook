# Building from source

## Repository layout

```
app/        Android client (Kotlin, Compose)
server/     Python FastAPI backend
extension/  Manifest-V3 browser extension
docs/       this documentation
gradle/     version catalog (libs.versions.toml) + wrapper
```

## Android app

**Toolchain:** AGP 9 with built-in Kotlin 2.2 (no separate `kotlin-android` plugin), Compose
compiler + KSP pinned to the Kotlin version, Hilt ≥ 2.59, Java 17. `minSdk 30`, `targetSdk 36`,
`compileSdk 36`. Dependencies are declared in the version catalog `gradle/libs.versions.toml`
(referenced as `libs.*`) — never hardcode versions.

You need a JDK 17–21 on `JAVA_HOME` (for example the JBR bundled with Android Studio).

```bash
./gradlew assembleDebug              # build the debug APK
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew testDebugUnitTest --tests "com.food.opencook.SharedVectorsTest"
./gradlew connectedAndroidTest       # instrumented tests (device/emulator)
./gradlew lint
```

The SDK location goes in `local.properties` (`sdk.dir=…`), which is git-ignored.

## Releasing

Releases go out via GitHub releases and F-Droid — there is no in-app updater.

1. **Bump the version.** In `app/build.gradle.kts` raise `versionCode` (and usually `versionName`)
   for every release.

2. **Build a signed release APK.** Signing is wired in `app/build.gradle.kts` from a
   `../openCook.keystore` properties file (or env vars); without it the release build is unsigned.
   ```bash
   ./gradlew assembleRelease   # → app/build/outputs/apk/release/openCook-<versionName>-release.apk
   ```

3. **Tag and publish.** Tag the commit `v<versionName>` and attach the signed
   `openCook-<versionName>-release.apk` to the GitHub release. F-Droid picks up new tags
   automatically (`UpdateCheckMode: Tags`), builds the release variant reproducibly and ships your
   signed APK — see the reference recipe at `fdroid/com.food.opencook.yml`.

(A CI workflow also publishes a rolling debug APK as the `dev-build` prerelease on every push to
`main`; see `.github/workflows/ci.yml`.)

## Server

Requires Python 3.12+ and (for extraction) Ollama on the host with `qwen2.5vl:7b` pulled.

```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app --reload    # http://localhost:8000/docs
pytest -q                         # tests
```

Dependencies (`pyproject.toml`): FastAPI, Uvicorn, SQLAlchemy 2, pydantic-settings,
python-multipart, httpx, Pillow, zeroconf. Dev extra: pytest.

## Browser extension

Pure vanilla JS, no build step. Load it unpacked from `extension/` in a Chromium browser
(`chrome://extensions` → developer mode → "Load unpacked"). Details: `extension/README.md`.

## Conventions

- **Source comments in English** (product/design docs are German).
- Add app dependencies via the version catalog, not inline versions.
- Server config is environment-variable driven (`OPENCOOK_*`) for trivial Docker packaging.
- App identifiers: package/namespace/applicationId `com.food.opencook`.

## A note on the shared sync fixture

`server/tests/fixtures/sync-vectors.json` is consumed by **both** the Python tests
(`server/tests/test_sync.py`) and the Kotlin test (`app/.../SharedVectorsTest.kt`). If you touch the
sync engine on either side, keep both test suites green. See [Sync engine](sync.md).
