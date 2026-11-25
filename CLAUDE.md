# Spoil Not
- Source of truth: docs/DESIGN.md. Deviations go in docs/DECISIONS.md (date, decision, reason).
- v1 (M0–M7) is done. Every change from now on, in the same PR: update the DESIGN sections it affects, add a row to
  DESIGN section 17 (Changes after v1), add DECISIONS entries, and keep tests and the other docs (RUNBOOK, QA,
  PRIVACY, SECURITY_REVIEW) at the milestones' quality bar. A DECISIONS entry that changes what a DESIGN sentence
  says rewrites that sentence too. Each fact lives in one doc and the others link to it (DECISIONS 2026-11-24).
- Android app: Kotlin, Jetpack Compose with Material 3, Navigation 3, Hilt, Coroutines and Flow, Firebase (Auth, Firestore, FCM) on the free Spark plan. Latest stable versions through gradle/libs.versions.toml.
- Web app for iPhone in `web/` (DESIGN 18): Vite, React, TypeScript, the Firebase web SDK on the same project. Same look as the app: colors, strings and icons are generated from the Android sources (`npm run gen`); only `web/src/data` imports Firebase; business logic in `web/src/domain` with an injectable clock and tests.
- Never add Cloud Functions, Cloud Storage or any paid Google Cloud service. Never commit secrets.
- Composables and ViewModels never use Firebase classes. A feature's Firestore code stays in its feature/*/data/ repositories; core/firebase holds the SDK instances and the shared Firestore code: the household writer (`FirestoreHouseholdWriter`), the listener helpers (`DeltaSync.kt`, `FirestoreFlows.kt`) and the field readers (`Fields.kt`).
- Business logic is pure Kotlin in feature/*/domain/ with an injectable java.time.Clock and unit tests.
- Dates are 'YYYY-MM-DD' strings; timestamps are server timestamps.
- Every destructive action has Undo. Status is never conveyed by color alone.
- All user-facing text lives in app/src/main/res/values/strings.xml.

## Toolchain (macOS, fish shell)
- JDK 21 (Homebrew openjdk@21) and Node 24 LTS (Homebrew node@24) are on the fish PATH. In non-fish shells:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21 PATH=/opt/homebrew/opt/node@24/bin:$PATH`
- Flavors: `dev` (com.parcha.spoilnot.dev, Firebase project spoilnot-dev) and `prod` (com.parcha.spoilnot, spoilnot-prod).
- Aggregate `lintDebug`/`testDebugUnitTest` tasks don't exist with flavors; use the dev variants below.
- `adb` isn't on the PATH: use `~/Library/Android/sdk/platform-tools/adb`, as the scripts do. A worktree has no
  `local.properties`, so run Gradle there with `ANDROID_HOME=~/Library/Android/sdk`.

## Emulators
- `./scripts/emulators.sh` starts Auth (9099), Firestore (8080) and the UI (http://127.0.0.1:4000) with the emulator-only project `demo-spoilnot`.
- Dev debug builds connect to them at 10.0.2.2 (`-Pspoilnot.emulatorHost=<LAN IP>` for a physical phone) with project ID `demo-spoilnot` from `app/src/dev/debug/google-services.json`. All other builds use real Firebase.
- On Android 17+ dev debug builds need ACCESS_LOCAL_NETWORK to reach 10.0.2.2; the app asks once and tests grant it.

## Patterns
- Repositories read `FirestoreProvider.current` or collect `.instance`; never keep a `FirebaseFirestore` reference (it is replaced when local data is cleared).
- Household-scoped listeners use `snapshotFlow().retryAfterOwnWrites(db)`; products, stock, grocery items, recipes and
  the meal plan (with a window of dates) use `liveDocuments(db, syncLog)`, and Recent activity `liveNewest`
  (changes-only, DESIGN 9.8). Deleting any of those but activity writes a tombstone (`Tombstones.fields`); every
  reader skips documents with `deletedAt`. DESIGN 9.1's table lists every listener and how long it lives.
- Session state (signed out, gate, joining, in household) comes only from `SessionManager`.
- Actions go through the feature's service (`PantryService`, `GroceryService`, `RecipeService`, `MealPlanService`), which returns change plans (`PantryChange`, `GroceryChange`, …); `HouseholdWriter.apply` writes them. Destructive actions return an `Undo`.
- String resources: escape apostrophes (`\'`). A bad string fails the resource build, not `compileDevDebugKotlin`, so check `BUILD SUCCESSFUL`.

## Before finishing a task
- `./gradlew spotlessApply` then `./gradlew spotlessCheck lintDevDebug testDevDebugUnitTest assembleDebug compileDevDebugAndroidTestKotlin :benchmark:compileBenchmarkKotlin`
- `cd firebase && npm test` (rules tests in the Firestore emulator)
- Reminder job changes (`backend/`): `cd backend/reminder && npm run typecheck && npm test && npm run build`, then with
  the emulators running `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run test:integration`
- Web changes, or Android strings/theme changes: `cd web && npm run gen:check && npm run check && npm run e2e` (look at
  changed screenshots; `gen:check` regenerates and fails while the generated files differ from git, so stage them
  first), then `npm run e2e:preview` (the build under the hosting headers, as CI)
- `./gradlew verifyRoborazziDevDebug` (compares the screenshots with the references committed in
  `app/src/test/screenshots`; Mac only, CI skips it). A failure writes `*_compare.png` to `app/build/outputs/roborazzi`.
- After an intended UI change: `./gradlew recordRoborazziDevDebug`, look at every changed image in
  `app/src/test/screenshots` (`git status`; light/dark, font 1.0/2.0), and commit them with the change. Never
  re-record to make an unexpected difference go away.
- With emulators and an Android emulator running: `./gradlew connectedDevDebugAndroidTest` (includes the two-user household tests)
- Recipe import changes: also run `LiveImportTest` against the real sites (command in docs/import-test-sites.md)
- Changes to listeners or scheduled jobs: `cd firebase && npm run quota-sim` and compare with docs/RUNBOOK.md
- Font changes: `./scripts/subset-fonts.sh` (needs `brew install harfbuzz`); `FontSubsetTest` checks the subsets
- Large UI changes (new screens or lists) and Compose, Firebase or DataStore updates: `./scripts/baseline-profile.sh`
  on the headless emulator from docs/RUNBOOK.md "Benchmark setup" (the script refuses software rendering) and
  commit the new profile
