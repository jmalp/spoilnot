# Spoil Not
- Source of truth: docs/DESIGN.md. Deviations go in docs/DECISIONS.md (date, decision, reason).
- Android only. Stack: Kotlin, Jetpack Compose with Material 3, Navigation 3, Hilt, Coroutines and Flow, Firebase (Auth, Firestore, FCM) on the free Spark plan. Latest stable versions through gradle/libs.versions.toml.
- Never add Cloud Functions, Cloud Storage or any paid Google Cloud service. Never commit secrets.
- Composables and ViewModels never use Firebase classes. Only feature/*/data/ repositories touch Firestore (core/firebase only provides the SDK instances).
- Business logic is pure Kotlin in feature/*/domain/ with an injectable java.time.Clock and unit tests.
- Dates are 'YYYY-MM-DD' strings; timestamps are server timestamps.
- Every destructive action has Undo. Status is never conveyed by color alone.
- All user-facing text lives in app/src/main/res/values/strings.xml.

## Toolchain (macOS, fish shell)
- JDK 21 (Homebrew openjdk@21) and Node 24 LTS (Homebrew node@24) are on the fish PATH. In non-fish shells:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/node@24/bin:$PATH`
- Flavors: `dev` (com.parcha.spoilnot.dev, Firebase project spoilnot-dev) and `prod` (com.parcha.spoilnot, spoilnot-prod).
- Aggregate `lintDebug`/`testDebugUnitTest` tasks don't exist with flavors; use the dev variants below.

## Emulators
- `./scripts/emulators.sh` starts Auth (9099), Firestore (8080) and the UI (http://127.0.0.1:4000) with the emulator-only project `demo-spoilnot`.
- Dev debug builds connect to them at 10.0.2.2 (`-Pspoilnot.emulatorHost=<LAN IP>` for a physical phone) with project ID `demo-spoilnot` from `app/src/dev/debug/google-services.json`. All other builds use real Firebase.
- On Android 17+ dev debug builds need ACCESS_LOCAL_NETWORK to reach 10.0.2.2; the app asks once and tests grant it.

## Patterns
- Repositories read `FirestoreProvider.current` or collect `.instance`; never keep a `FirebaseFirestore` reference (it is replaced when local data is cleared).
- Household-scoped listeners use `snapshotFlow().retryAfterOwnWrites(db)`.
- Session state (signed out, gate, joining, in household) comes only from `SessionManager`.
- Pantry actions go through `PantryService`, which returns `PantryChange` plans; `PantryRepository.apply` writes them. Destructive actions return an `Undo`.
- String resources: escape apostrophes (`\'`). A bad string fails the resource build, not `compileDevDebugKotlin`, so check `BUILD SUCCESSFUL`.

## Before finishing a task
- `./gradlew spotlessApply` then `./gradlew spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug`
- `cd firebase && npm test` (rules tests in the Firestore emulator)
- After UI changes: `./gradlew recordRoborazziDevDebug` and look at `app/build/outputs/roborazzi/*.png` (light/dark, font 1.0/2.0)
- With emulators and an Android emulator running: `./gradlew connectedDevDebugAndroidTest` (includes the two-user household tests)
- Recipe import changes: also run `LiveImportTest` against the real sites (command in docs/import-test-sites.md)
